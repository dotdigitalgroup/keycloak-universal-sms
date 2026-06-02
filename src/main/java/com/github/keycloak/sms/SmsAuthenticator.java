package com.github.keycloak.sms;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.jboss.logging.Logger;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.Map;

/**
 * Keycloak Authenticator that sends a time-limited OTP via SMS and validates
 * the code entered by the user.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@link #authenticate} — generate OTP, send SMS, present challenge form.</li>
 *   <li>{@link #action}       — validate submitted code; retry or succeed.</li>
 * </ol>
 *
 * <h3>Session notes used</h3>
 * <ul>
 *   <li>{@code sms_code}      — the generated OTP (stored in auth session)</li>
 *   <li>{@code sms_code_ttl}  — expiry epoch in milliseconds</li>
 *   <li>{@code sms_phone}     — normalised phone number (for display)</li>
 * </ul>
 */
public class SmsAuthenticator implements Authenticator {

    private static final Logger log = Logger.getLogger(SmsAuthenticator.class);

    static final String NOTE_CODE      = "sms_code";
    static final String NOTE_TTL       = "sms_code_ttl";
    static final String NOTE_PHONE     = "sms_phone";
    static final String FORM_CODE      = "code";
    static final String TEMPLATE_NAME  = "sms-validation.ftl";

    private static final SecureRandom RANDOM = new SecureRandom();

    // -------------------------------------------------------------------------
    // authenticate() — called first time this step is reached
    // -------------------------------------------------------------------------

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();

        // Retrieve phone from user attribute
        String rawPhone = user.getFirstAttribute("phoneNumber");
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            log.warnf("User %s has no phoneNumber attribute; cannot send SMS", user.getUsername());
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_USER,
                    context.form()
                           .setError("smsAuthNoPhone")
                           .createForm(TEMPLATE_NAME));
            return;
        }

        Map<String, String> config = resolveConfig(context);
        String countryCode = config.get(SmsAuthenticatorFactory.CONF_COUNTRY_CODE);

        // Normalise phone number
        String phone;
        try {
            phone = PhoneNumberNormaliser.normalise(rawPhone, countryCode);
        } catch (IllegalArgumentException e) {
            log.warnf("Could not normalise phone '%s' for user %s: %s",
                     GenericHttpSmsSender.mask(rawPhone), user.getUsername(), e.getMessage());
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_USER,
                    context.form()
                           .setError("smsAuthInvalidPhone")
                           .createForm(TEMPLATE_NAME));
            return;
        }

        // Generate OTP
        int otpLength = parseInt(config.get(SmsAuthenticatorFactory.CONF_OTP_LENGTH), 6);
        int ttlMinutes = parseInt(config.get(SmsAuthenticatorFactory.CONF_OTP_TTL), 5);
        String code    = generateCode(otpLength);
        long   expiry  = System.currentTimeMillis() + (ttlMinutes * 60_000L);

        // Store in auth-session
        context.getAuthenticationSession().setAuthNote(NOTE_CODE,  code);
        context.getAuthenticationSession().setAuthNote(NOTE_TTL,   String.valueOf(expiry));
        context.getAuthenticationSession().setAuthNote(NOTE_PHONE,  phone);

        // Build sender from config
        SmsSender sender = buildSender(config);

        // Send SMS — OTP is NEVER logged in plain text
        try {
            sender.send(phone, code);
            log.infof("OTP dispatched to %s for user %s", GenericHttpSmsSender.mask(phone), user.getUsername());
        } catch (SmsSendException e) {
            log.errorf(e, "Failed to send SMS to %s for user %s (HTTP %s): %s",
                      GenericHttpSmsSender.mask(phone), user.getUsername(),
                      e.getHttpStatusCode(), e.getMessage());
            context.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    context.form()
                           .setError("smsAuthSendFailed")
                           .createForm(TEMPLATE_NAME));
            return;
        }

        // Show challenge form
        Response challenge = context.form()
                .setAttribute("phoneNumber", obfuscatePhone(phone))
                .setAttribute("ttlMinutes",  ttlMinutes)
                .createForm(TEMPLATE_NAME);
        context.challenge(challenge);
    }

    // -------------------------------------------------------------------------
    // action() — called when the user submits the form
    // -------------------------------------------------------------------------

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();

        if (params.containsKey("resend")) {
            log.infof("User %s requested a new OTP code", context.getUser().getUsername());
            clearNotes(context);
            authenticate(context);
            return;
        }

        String submittedCode = params.getFirst(FORM_CODE);

        String storedCode = context.getAuthenticationSession().getAuthNote(NOTE_CODE);
        String storedTtl  = context.getAuthenticationSession().getAuthNote(NOTE_TTL);
        String phone      = context.getAuthenticationSession().getAuthNote(NOTE_PHONE);

        // Guard: session data missing (e.g. session expired)
        if (storedCode == null || storedTtl == null) {
            log.warnf("SMS auth session notes missing for user %s; aborting",
                     context.getUser().getUsername());
            context.failureChallenge(
                    AuthenticationFlowError.EXPIRED_CODE,
                    context.form()
                           .setError("smsAuthCodeExpired")
                           .createForm(TEMPLATE_NAME));
            return;
        }

        // TTL check
        long expiry = Long.parseLong(storedTtl);
        if (System.currentTimeMillis() > expiry) {
            log.infof("OTP expired for user %s (%s)", context.getUser().getUsername(),
                     GenericHttpSmsSender.mask(phone));
            clearNotes(context);
            context.failureChallenge(
                    AuthenticationFlowError.EXPIRED_CODE,
                    context.form()
                           .setAttribute("phoneNumber", obfuscatePhone(phone))
                           .setError("smsAuthCodeExpired")
                           .createForm(TEMPLATE_NAME));
            return;
        }

        // Code check (constant-time comparison to resist timing attacks)
        if (!constantTimeEquals(storedCode, submittedCode == null ? "" : submittedCode.trim())) {
            log.warnf("Invalid OTP submitted by user %s; sent to %s",
                     context.getUser().getUsername(), GenericHttpSmsSender.mask(phone));
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form()
                           .setAttribute("phoneNumber", obfuscatePhone(phone))
                           .setError("smsAuthInvalidCode")
                           .createForm(TEMPLATE_NAME));
            return;
        }

        // Success
        log.infof("OTP validated successfully for user %s (%s)",
                 context.getUser().getUsername(), GenericHttpSmsSender.mask(phone));
        clearNotes(context);
        context.success();
    }

    // -------------------------------------------------------------------------
    // Keycloak lifecycle
    // -------------------------------------------------------------------------

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        String phone = user.getFirstAttribute("phoneNumber");
        return phone != null && !phone.trim().isEmpty();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // Optionally add a VERIFY_PHONE_NUMBER required action here
    }

    @Override
    public void close() {
        // nothing to close
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Map<String, String> resolveConfig(AuthenticationFlowContext context) {
        AuthenticatorConfigModel configModel = context.getAuthenticatorConfig();
        return (configModel != null && configModel.getConfig() != null)
               ? configModel.getConfig()
               : Map.of();
    }

    private SmsSender buildSender(Map<String, String> config) {
        String apiUrl     = config.getOrDefault(SmsAuthenticatorFactory.CONF_API_URL,     "");
        String httpMethod = config.getOrDefault(SmsAuthenticatorFactory.CONF_HTTP_METHOD, "POST");
        String authHeader = config.getOrDefault(SmsAuthenticatorFactory.CONF_AUTH_HEADER, "");
        String payload    = config.getOrDefault(SmsAuthenticatorFactory.CONF_PAYLOAD_TPL, "");
        return new GenericHttpSmsSender(apiUrl, httpMethod, authHeader, payload);
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** Obfuscates phone for display: +8801712345678 → +880 171****678 */
    private String obfuscatePhone(String phone) {
        if (phone == null || phone.length() < 8) return "****";
        int len = phone.length();
        return phone.substring(0, Math.min(5, len - 4))
               + "****"
               + phone.substring(len - 3);
    }

    /** Constant-time string equality to mitigate timing side-channels. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private void clearNotes(AuthenticationFlowContext context) {
        context.getAuthenticationSession().removeAuthNote(NOTE_CODE);
        context.getAuthenticationSession().removeAuthNote(NOTE_TTL);
        // Keep NOTE_PHONE so the error page can still display the obfuscated number
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

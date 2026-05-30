package com.github.keycloak.sms;

import com.github.keycloak.sms.profile.PhoneNumberInputs;
import com.github.keycloak.sms.profile.PhoneNumberSupport;
import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Identifies the user by {@code phoneNumber} at the start of the flow (like Username Form).
 */
public class PhoneNumberFormAuthenticator implements Authenticator {

    static final String TEMPLATE = "phone-number-form.ftl";

    private static final PhoneNumberFormAuthenticator INSTANCE = new PhoneNumberFormAuthenticator();

    static PhoneNumberFormAuthenticator getInstance() {
        return INSTANCE;
    }

    private PhoneNumberFormAuthenticator() {
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        context.challenge(context.form().createForm(TEMPLATE));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();
        String raw = params.getFirst(PhoneNumberInputs.FORM_FIELD);

        if (raw == null || raw.trim().isEmpty()) {
            context.challenge(
                    context.form()
                            .setError(PhoneNumberInputs.MSG_REQUIRED)
                            .createForm(TEMPLATE));
            return;
        }

        final Set<String> candidates;
        try {
            candidates = PhoneNumberSupport.searchCandidates(raw, resolveCountryCode(context));
        } catch (IllegalArgumentException e) {
            PhoneNumberSupport.logNormaliseFailure(raw, null, e);
            challengeNotFound(context);
            return;
        }

        RealmModel realm = context.getRealm();
        KeycloakSession session = context.getSession();
        Map<String, UserModel> matches = new LinkedHashMap<>();
        for (String candidate : candidates) {
            session.users()
                    .searchForUserByUserAttributeStream(realm, PhoneNumberInputs.ATTRIBUTE, candidate)
                    .forEach(user -> matches.putIfAbsent(user.getId(), user));
        }

        if (matches.size() != 1) {
            challengeNotFound(context);
            return;
        }

        UserModel user = matches.values().iterator().next();
        if (!user.isEnabled()) {
            challengeNotFound(context);
            return;
        }

        context.setUser(user);
        context.success();
    }

    private static void challengeNotFound(AuthenticationFlowContext context) {
        context.clearUser();
        context.getEvent().error(Errors.USER_NOT_FOUND);
        context.challenge(
                context.form()
                        .setError(PhoneNumberInputs.MSG_NOT_FOUND)
                        .createForm(TEMPLATE));
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }

    private static String resolveCountryCode(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null || config.getConfig() == null) {
            return null;
        }
        return PhoneNumberSupport.countryCodeFromMap(config.getConfig());
    }
}

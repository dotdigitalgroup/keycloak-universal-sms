package com.github.keycloak.sms;

import com.github.keycloak.sms.profile.PhoneNumberInputs;
import com.github.keycloak.sms.profile.PhoneNumberSupport;
import com.github.keycloak.sms.requiredaction.UpdatePhoneNumberRequiredAction;
import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Authentication step that collects {@code phoneNumber} when the user attribute is missing.
 */
public class CollectPhoneNumberAuthenticator implements Authenticator {

    static final String TEMPLATE = "collect-phone-number.ftl";

    private static final CollectPhoneNumberAuthenticator INSTANCE = new CollectPhoneNumberAuthenticator();

    static CollectPhoneNumberAuthenticator getInstance() {
        return INSTANCE;
    }

    private CollectPhoneNumberAuthenticator() {
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        String existing = user.getFirstAttribute(PhoneNumberInputs.ATTRIBUTE);
        if (existing != null && !existing.trim().isEmpty()) {
            context.success();
            return;
        }
        context.challenge(context.form().createForm(TEMPLATE));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();
        String raw = params.getFirst(PhoneNumberInputs.FORM_FIELD);
        UserModel user = context.getUser();

        if (raw == null || raw.trim().isEmpty()) {
            context.challenge(
                    context.form()
                            .setError(PhoneNumberInputs.MSG_REQUIRED)
                            .createForm(TEMPLATE));
            return;
        }

        try {
            String normalized = PhoneNumberSupport.normalise(raw, resolveCountryCode(context));
            user.setSingleAttribute(PhoneNumberInputs.ATTRIBUTE, normalized);
            context.success();
        } catch (IllegalArgumentException e) {
            PhoneNumberSupport.logNormaliseFailure(raw, user.getUsername(), e);
            context.challenge(
                    context.form()
                            .setError(PhoneNumberInputs.MSG_INVALID)
                            .createForm(TEMPLATE));
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        String phone = user.getFirstAttribute(PhoneNumberInputs.ATTRIBUTE);
        return phone != null && !phone.trim().isEmpty();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        if (!configuredFor(session, realm, user)) {
            user.addRequiredAction(UpdatePhoneNumberRequiredAction.PROVIDER_ID);
        }
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

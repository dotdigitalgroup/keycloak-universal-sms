package com.github.keycloak.sms.form;

import com.github.keycloak.sms.profile.PhoneNumberInputs;
import com.github.keycloak.sms.profile.PhoneNumberSupport;
import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;

import java.util.List;

/**
 * Registration form action that validates and persists {@code phoneNumber} on the new user.
 */
public class RegistrationPhoneNumberFormAction implements FormAction {

    private static final RegistrationPhoneNumberFormAction INSTANCE = new RegistrationPhoneNumberFormAction();

    static RegistrationPhoneNumberFormAction getInstance() {
        return INSTANCE;
    }

    private RegistrationPhoneNumberFormAction() {
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        form.setAttribute("phoneNumberRegistrationEnabled", Boolean.TRUE);
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String raw = formData.getFirst(PhoneNumberInputs.FORM_FIELD);

        if (raw == null || raw.trim().isEmpty()) {
            context.validationError(
                    formData,
                    List.of(new FormMessage(PhoneNumberInputs.FORM_FIELD, PhoneNumberInputs.MSG_REQUIRED)));
            return;
        }

        try {
            PhoneNumberSupport.searchKey(raw, resolveCountryCode(context));
            context.success();
        } catch (IllegalArgumentException e) {
            context.validationError(
                    formData,
                    List.of(new FormMessage(PhoneNumberInputs.FORM_FIELD, PhoneNumberInputs.MSG_INVALID)));
        }
    }

    @Override
    public void success(FormContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String raw = formData.getFirst(PhoneNumberInputs.FORM_FIELD);
        if (raw == null || raw.isBlank()) {
            return;
        }
        UserModel user = context.getUser();
        if (user == null) {
            return;
        }
        user.setSingleAttribute(PhoneNumberInputs.ATTRIBUTE, raw.trim());
        try {
            user.setSingleAttribute(
                    PhoneNumberInputs.SEARCH_ATTRIBUTE,
                    PhoneNumberSupport.searchKey(raw, resolveCountryCode(context)));
        } catch (IllegalArgumentException e) {
            PhoneNumberSupport.logNormaliseFailure(raw, user.getUsername(), e);
        }
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

    private static String resolveCountryCode(FormContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null || config.getConfig() == null) {
            return null;
        }
        return PhoneNumberSupport.countryCodeFromMap(config.getConfig());
    }
}

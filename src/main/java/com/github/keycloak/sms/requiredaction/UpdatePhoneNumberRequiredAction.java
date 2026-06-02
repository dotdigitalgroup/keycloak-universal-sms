package com.github.keycloak.sms.requiredaction;

import com.github.keycloak.sms.profile.PhoneNumberInputs;
import com.github.keycloak.sms.profile.PhoneNumberSupport;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;

/**
 * Required action that prompts the user to set or update {@code phoneNumber}.
 */
public class UpdatePhoneNumberRequiredAction implements RequiredActionProvider {

    public static final String PROVIDER_ID = "UPDATE_PHONE_NUMBER";
    static final String TEMPLATE = "update-phone-number.ftl";

    private static final UpdatePhoneNumberRequiredAction INSTANCE = new UpdatePhoneNumberRequiredAction();

    static UpdatePhoneNumberRequiredAction getInstance() {
        return INSTANCE;
    }

    private UpdatePhoneNumberRequiredAction() {
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // no automatic triggers
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Response challenge = context.form().createForm(TEMPLATE);
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String raw = formData.getFirst(PhoneNumberInputs.FORM_FIELD);
        UserModel user = context.getUser();

        if (raw == null || raw.trim().isEmpty()) {
            context.challenge(
                    context.form()
                            .addError(new FormMessage(PhoneNumberInputs.FORM_FIELD, PhoneNumberInputs.MSG_REQUIRED))
                            .createForm(TEMPLATE));
            return;
        }

        try {
            String searchKey = PhoneNumberSupport.searchKey(raw, resolveCountryCode(context));
            user.setSingleAttribute(PhoneNumberInputs.ATTRIBUTE, raw.trim());
            user.setSingleAttribute(PhoneNumberInputs.SEARCH_ATTRIBUTE, searchKey);
            user.removeRequiredAction(PROVIDER_ID);
            context.success();
        } catch (IllegalArgumentException e) {
            PhoneNumberSupport.logNormaliseFailure(raw, user.getUsername(), e);
            context.challenge(
                    context.form()
                            .addError(new FormMessage(PhoneNumberInputs.FORM_FIELD, PhoneNumberInputs.MSG_INVALID))
                            .createForm(TEMPLATE));
        }
    }

    @Override
    public void close() {
    }

    private static String resolveCountryCode(RequiredActionContext context) {
        RequiredActionProviderModel model = context.getRealm()
                .getRequiredActionProviderByAlias(PROVIDER_ID);
        if (model == null || model.getConfig() == null) {
            return null;
        }
        return PhoneNumberSupport.countryCodeFromMap(model.getConfig());
    }
}

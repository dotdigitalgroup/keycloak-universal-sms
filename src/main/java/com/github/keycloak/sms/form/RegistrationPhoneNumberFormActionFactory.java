package com.github.keycloak.sms.form;

import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import com.github.keycloak.sms.profile.PhoneNumberInputs;

import java.util.List;

public class RegistrationPhoneNumberFormActionFactory implements FormActionFactory {

    public static final String PROVIDER_ID = "registration-phone-number";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = ProviderConfigurationBuilder.create()
            .property()
                .name(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE)
                .label("Default Country Code")
                .helpText(
                        "Country calling code (digits only, no '+') for local numbers starting with 0.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("")
                .add()
            .build();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Registration Phone Number";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED,
        };
    }

    @Override
    public String getHelpText() {
        return "Collects and normalises phoneNumber during self-registration.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return RegistrationPhoneNumberFormAction.getInstance();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}

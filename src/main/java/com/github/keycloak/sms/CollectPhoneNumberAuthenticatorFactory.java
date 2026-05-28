package com.github.keycloak.sms;

import com.github.keycloak.sms.profile.PhoneNumberInputs;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

public class CollectPhoneNumberAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "collect-phone-number";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = ProviderConfigurationBuilder.create()
            .property()
                .name(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE)
                .label("Default Country Code")
                .helpText(
                        "Country calling code (digits only, no '+') for local numbers starting with 0. "
                                + "Matches the SMS authenticator setting when both are used.")
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
        return "Collect Phone Number";
    }

    @Override
    public String getReferenceCategory() {
        return "phone";
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
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED,
        };
    }

    @Override
    public String getHelpText() {
        return "Prompts the user to enter a phone number when the phoneNumber attribute is empty.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return CollectPhoneNumberAuthenticator.getInstance();
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

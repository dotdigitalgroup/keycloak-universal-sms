package com.github.keycloak.sms.profile;

import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.validate.AbstractStringValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidationResult;
import org.keycloak.validate.ValidatorConfig;
import org.keycloak.validate.validators.ValidatorConfigValidator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Declarative User Profile validator ({@code phone-number}) that normalises input to E.164
 * before persistence.
 */
public class PhoneNumberValidator extends AbstractStringValidator implements ConfiguredProvider {

    public static final PhoneNumberValidator INSTANCE = new PhoneNumberValidator();

    public static final String ID = "phone-number";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        ProviderConfigProperty property = new ProviderConfigProperty();
        property.setName(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE);
        property.setLabel("Default country code");
        property.setHelpText(
                "Country calling code without '+', prepended for local numbers starting with 0. "
                        + "Example: 55 for Brazil, 880 for Bangladesh.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        CONFIG_PROPERTIES.add(property);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected void doValidate(String value, String inputHint, ValidationContext context, ValidatorConfig config) {
        try {
            String searchKey = PhoneNumberSupport.searchKey(value, PhoneNumberSupport.countryCodeFromConfig(config));
            PhoneNumberSupport.writeSearchAttribute(context, searchKey);
        } catch (IllegalArgumentException e) {
            context.addError(new ValidationError(ID, inputHint, PhoneNumberInputs.MSG_INVALID));
        }
    }

    @Override
    public ValidationResult validateConfig(KeycloakSession session, ValidatorConfig config) {
        Set<ValidationError> errors = new LinkedHashSet<>();
        if (config != null && config.containsKey(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE)) {
            String cc = config.getString(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE);
            if (cc != null && !cc.matches("\\d+")) {
                errors.add(new ValidationError(
                        ID,
                        PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE,
                        ValidatorConfigValidator.MESSAGE_CONFIG_INVALID_VALUE,
                        cc));
            }
        }
        return new ValidationResult(errors);
    }

    @Override
    public String getHelpText() {
        return "Validates and normalises phone numbers to E.164 format.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }
}

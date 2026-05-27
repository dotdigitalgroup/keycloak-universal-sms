package com.github.keycloak.sms.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.userprofile.AttributeContext;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhoneNumberValidator")
class PhoneNumberValidatorTest {

    private final PhoneNumberValidator validator = PhoneNumberValidator.INSTANCE;

    @Test
    @DisplayName("normalises masked Brazilian number and rewrites profile attribute")
    void normalisesAndRewritesAttribute() {
        List<String> values = new ArrayList<>(List.of("+55 (11) 91234-5678"));
        UserProfileAttributeValidationContext context = contextWithValues(values);
        ValidatorConfig config = ValidatorConfig.configFromMap(
                Map.of(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE, "55"));

        validator.validate("+55 (11) 91234-5678", PhoneNumberInputs.ATTRIBUTE, context, config);

        assertTrue(context.isValid());
        assertEquals("+5511912345678", values.get(0));
    }

    @Test
    @DisplayName("normalises local number with default country code")
    void normalisesLocalWithCountryCode() {
        List<String> values = new ArrayList<>(List.of("011912345678"));
        UserProfileAttributeValidationContext context = contextWithValues(values);
        ValidatorConfig config = ValidatorConfig.configFromMap(
                Map.of(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE, "55"));

        validator.validate("011912345678", PhoneNumberInputs.ATTRIBUTE, context, config);

        assertTrue(context.isValid());
        assertEquals("+5511912345678", values.get(0));
    }

    @Test
    @DisplayName("reports error for invalid number")
    void invalidNumberAddsError() {
        UserProfileAttributeValidationContext context =
                new UserProfileAttributeValidationContext(mock(AttributeContext.class));
        ValidatorConfig config = ValidatorConfig.EMPTY;

        validator.validate("not-a-phone", PhoneNumberInputs.ATTRIBUTE, context, config);

        assertFalse(context.isValid());
        assertTrue(context.getErrors().stream()
                .map(ValidationError::getMessage)
                .anyMatch(PhoneNumberInputs.MSG_INVALID::equals));
    }

    private static UserProfileAttributeValidationContext contextWithValues(List<String> values) {
        AttributeContext attributeContext = mock(AttributeContext.class);
        when(attributeContext.getAttribute()).thenReturn(Map.entry(PhoneNumberInputs.ATTRIBUTE, values));
        return new UserProfileAttributeValidationContext(attributeContext);
    }
}

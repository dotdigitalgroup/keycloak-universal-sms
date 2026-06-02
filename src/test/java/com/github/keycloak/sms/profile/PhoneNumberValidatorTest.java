package com.github.keycloak.sms.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.UserModel;
import org.keycloak.userprofile.AttributeContext;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhoneNumberValidator")
class PhoneNumberValidatorTest {

    private final PhoneNumberValidator validator = PhoneNumberValidator.INSTANCE;

    @Test
    @DisplayName("writes canonical search index for masked number without rewriting phoneNumber")
    void writesSearchIndexForMaskedNumber() {
        UserModel user = mock(UserModel.class);
        UserProfileAttributeValidationContext context = contextWithUser(user);
        ValidatorConfig config = ValidatorConfig.configFromMap(
                Map.of(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE, "55"));

        validator.validate("+55 (11) 91234-5678", PhoneNumberInputs.ATTRIBUTE, context, config);

        assertTrue(context.isValid());
        verify(user).setSingleAttribute(PhoneNumberInputs.SEARCH_ATTRIBUTE, "5511912345678");
        verify(user, never()).setSingleAttribute(eq(PhoneNumberInputs.ATTRIBUTE), anyString());
    }

    @Test
    @DisplayName("writes canonical search index for local number with default country code")
    void writesSearchIndexForLocalNumber() {
        UserModel user = mock(UserModel.class);
        UserProfileAttributeValidationContext context = contextWithUser(user);
        ValidatorConfig config = ValidatorConfig.configFromMap(
                Map.of(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE, "55"));

        validator.validate("011912345678", PhoneNumberInputs.ATTRIBUTE, context, config);

        assertTrue(context.isValid());
        verify(user).setSingleAttribute(PhoneNumberInputs.SEARCH_ATTRIBUTE, "5511912345678");
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

    private static UserProfileAttributeValidationContext contextWithUser(UserModel user) {
        AttributeContext attributeContext = mock(AttributeContext.class);
        when(attributeContext.getUser()).thenReturn(user);
        return new UserProfileAttributeValidationContext(attributeContext);
    }
}

package com.github.keycloak.sms.profile;

/**
 * Shared constants for the {@code phoneNumber} user attribute across validators,
 * form actions, required actions, and authenticators.
 */
public final class PhoneNumberInputs {

    public static final String ATTRIBUTE = "phoneNumber";

    /**
     * Canonical digits-only index (E.164 without the leading {@code +}) used to look up users
     * regardless of the formatting/mask stored in {@link #ATTRIBUTE}.
     */
    public static final String SEARCH_ATTRIBUTE = "phoneNumberSearch";

    public static final String FORM_FIELD = "phoneNumber";

    public static final String CONF_DEFAULT_COUNTRY_CODE = "defaultCountryCode";

    public static final String MSG_INVALID = "phoneNumberInvalid";
    public static final String MSG_REQUIRED = "phoneNumberRequired";
    public static final String MSG_NOT_FOUND = "phoneNumberNotFound";

    private PhoneNumberInputs() {
    }
}

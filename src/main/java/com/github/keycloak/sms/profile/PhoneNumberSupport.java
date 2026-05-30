package com.github.keycloak.sms.profile;

import com.github.keycloak.sms.GenericHttpSmsSender;
import com.github.keycloak.sms.PhoneNumberNormaliser;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidatorConfig;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalises phone numbers and optionally rewrites user-profile attribute values.
 */
public final class PhoneNumberSupport {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberSupport.class);

    private PhoneNumberSupport() {
    }

    public static String emptyCountryCodeToNull(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return null;
        }
        return countryCode.trim();
    }

    public static String countryCodeFromConfig(ValidatorConfig config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        return emptyCountryCodeToNull(config.getString(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE));
    }

    public static String countryCodeFromMap(Map<String, String> config) {
        if (config == null) {
            return null;
        }
        return emptyCountryCodeToNull(config.get(PhoneNumberInputs.CONF_DEFAULT_COUNTRY_CODE));
    }

    /**
     * Normalises {@code raw} to E.164 or returns {@code null} when blank.
     *
     * @throws IllegalArgumentException when the value is non-blank but invalid
     */
    public static String normalise(String raw, String defaultCountryCode) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return PhoneNumberNormaliser.normalise(raw.trim(), emptyCountryCodeToNull(defaultCountryCode));
    }

    /**
     * E.164 variants to try when looking up a user by {@code phoneNumber} (e.g. phone-number-form).
     * First candidate is {@link #normalise}; an extra candidate prepends {@code defaultCountryCode}
     * for bare national numbers without {@code +}, {@code 00}, or leading {@code 0}.
     *
     * @throws IllegalArgumentException when no candidate can be built
     */
    public static Set<String> searchCandidates(String raw, String defaultCountryCode) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }

        String trimmed = raw.trim();
        String cc = emptyCountryCodeToNull(defaultCountryCode);

        try {
            candidates.add(PhoneNumberNormaliser.normalise(trimmed, cc));
        } catch (IllegalArgumentException ignored) {
            // may still add country-code-prefixed bare candidate below
        }

        if (cc != null) {
            try {
                PhoneNumberNormaliser.StrippedPhone stripped = PhoneNumberNormaliser.stripFormatting(trimmed);
                if (!stripped.hasPlus() && !stripped.startsWith00() && !stripped.startsWith0()) {
                    String digits = stripped.digits();
                    if (digits.matches("\\d+") && !digits.startsWith(cc)) {
                        candidates.add("+" + cc + digits);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // no bare candidate
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Phone number is not a valid search candidate");
        }
        return candidates;
    }

    public static void rewriteProfileAttribute(ValidationContext context, String normalized) {
        if (!(context instanceof UserProfileAttributeValidationContext profileContext)) {
            return;
        }
        Map.Entry<String, List<String>> attribute = profileContext.getAttributeContext().getAttribute();
        if (attribute == null) {
            return;
        }
        List<String> values = attribute.getValue();
        if (values == null || values.isEmpty()) {
            return;
        }
        values.set(0, normalized);
    }

    public static void logNormaliseFailure(String raw, String username, IllegalArgumentException e) {
        log.warn("Could not normalise phone '{}' for user {}: {}",
                GenericHttpSmsSender.mask(raw), username, e.getMessage());
    }
}

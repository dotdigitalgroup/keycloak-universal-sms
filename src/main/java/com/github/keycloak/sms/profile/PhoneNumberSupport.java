package com.github.keycloak.sms.profile;

import com.github.keycloak.sms.GenericHttpSmsSender;
import com.github.keycloak.sms.PhoneNumberNormaliser;
import org.keycloak.models.UserModel;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidatorConfig;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.jboss.logging.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalises phone numbers and optionally rewrites user-profile attribute values.
 */
public final class PhoneNumberSupport {

    private static final Logger log = Logger.getLogger(PhoneNumberSupport.class);

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

    /**
     * Canonical digits-only search key (E.164 without the leading {@code +}) for {@code raw},
     * or {@code null} when {@code raw} is blank.
     *
     * @throws IllegalArgumentException when the value is non-blank but invalid
     */
    public static String searchKey(String raw, String defaultCountryCode) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return PhoneNumberNormaliser.digitsOnly(
                PhoneNumberNormaliser.normalise(raw.trim(), emptyCountryCodeToNull(defaultCountryCode)));
    }

    /**
     * Digits-only search keys to look up a user by {@link PhoneNumberInputs#SEARCH_ATTRIBUTE}.
     * Mirrors {@link #searchCandidates} but strips the leading {@code +} so masked/unmasked
     * stored values converge on the same canonical index.
     *
     * @throws IllegalArgumentException when no candidate can be built
     */
    public static Set<String> searchKeys(String raw, String defaultCountryCode) {
        Set<String> candidates = searchCandidates(raw, defaultCountryCode);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String candidate : candidates) {
            keys.add(PhoneNumberNormaliser.digitsOnly(candidate));
        }
        return keys;
    }

    /**
     * Writes the canonical search index onto the user behind a declarative User Profile validation
     * context. Direct model write bypasses the unmanaged-attribute policy so the index always
     * persists. No-op when the user is not yet available (e.g. registration via User Profile),
     * which is covered by the imperative write paths and the backfill.
     */
    public static void writeSearchAttribute(ValidationContext context, String searchKey) {
        if (searchKey == null || !(context instanceof UserProfileAttributeValidationContext profileContext)) {
            return;
        }
        UserModel user = profileContext.getAttributeContext().getUser();
        if (user == null) {
            return;
        }
        user.setSingleAttribute(PhoneNumberInputs.SEARCH_ATTRIBUTE, searchKey);
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
        log.warnf("Could not normalise phone '%s' for user %s: %s",
                GenericHttpSmsSender.mask(raw), username, e.getMessage());
    }
}

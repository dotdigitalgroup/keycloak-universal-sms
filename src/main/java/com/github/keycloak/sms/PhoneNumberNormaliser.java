package com.github.keycloak.sms;

import java.util.regex.Pattern;

/**
 * Utility methods for sanitising and normalising phone numbers before they are
 * sent to an SMS gateway.
 *
 * <p>The normaliser:
 * <ol>
 *   <li>Strips all whitespace, dashes, dots, and parentheses.</li>
 *   <li>Converts a leading {@code 00} international prefix to {@code +}.</li>
 *   <li>Optionally prepends a country code when the number starts with a
 *       {@code 0} (local format) and a default country code is supplied.</li>
 * </ol>
 */
public final class PhoneNumberNormaliser {

    /** Allowed characters after stripping formatting: digits, leading '+'. */
    private static final Pattern STRIP = Pattern.compile("[\\s\\-.()+]");

    private PhoneNumberNormaliser() {
        // utility class
    }

    /**
     * Formatting stripped from {@code raw}, before country-code injection.
     */
    public static final class StrippedPhone {
        private final String digits;
        private final boolean hasPlus;
        private final boolean startsWith00;
        private final boolean startsWith0;

        StrippedPhone(String digits, boolean hasPlus, boolean startsWith00, boolean startsWith0) {
            this.digits = digits;
            this.hasPlus = hasPlus;
            this.startsWith00 = startsWith00;
            this.startsWith0 = startsWith0;
        }

        public String digits() {
            return digits;
        }

        public boolean hasPlus() {
            return hasPlus;
        }

        public boolean startsWith00() {
            return startsWith00;
        }

        public boolean startsWith0() {
            return startsWith0;
        }
    }

    /**
     * Removes formatting characters from {@code raw} without applying country-code rules.
     */
    public static StrippedPhone stripFormatting(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }
        String trimmed = raw.trim();
        boolean hasPlus = trimmed.startsWith("+");
        String digits = STRIP.matcher(trimmed).replaceAll("");
        return new StrippedPhone(digits, hasPlus, digits.startsWith("00"), !hasPlus && digits.startsWith("0"));
    }

    /**
     * Digits (and optional leading {@code +} removed) after stripping formatting only.
     */
    public static String digitsOnly(String raw) {
        return stripFormatting(raw).digits();
    }

    /**
     * Normalises a phone number to E.164 format.
     *
     * @param raw             raw phone number as typed by the user
     * @param defaultCountryCode country code WITHOUT the leading {@code +},
     *                           e.g. {@code "880"} for Bangladesh.
     *                           Pass {@code null} or empty to disable
     *                           country-code injection.
     * @return E.164-formatted phone number (e.g. {@code +8801712345678})
     * @throws IllegalArgumentException if the result contains non-digit
     *                                  characters (after stripping)
     */
    public static String normalise(String raw, String defaultCountryCode) {
        StrippedPhone strippedPhone = stripFormatting(raw);
        String stripped = strippedPhone.digits();
        boolean hasPlus = strippedPhone.hasPlus();

        if (strippedPhone.startsWith00()) {
            stripped = stripped.substring(2);
            hasPlus = true;
        }

        if (!hasPlus && stripped.startsWith("0")
                && defaultCountryCode != null && !defaultCountryCode.isEmpty()) {
            stripped = defaultCountryCode + stripped.substring(1);
            hasPlus = true;
        }

        if (!stripped.matches("\\d+")) {
            throw new IllegalArgumentException(
                    "Phone number contains invalid characters after normalisation: " + stripped);
        }

        return "+" + stripped;
    }

    /**
     * Convenience overload that uses no default country code.
     * Use when the user is expected to enter full international numbers.
     */
    public static String normalise(String raw) {
        return normalise(raw, null);
    }
}

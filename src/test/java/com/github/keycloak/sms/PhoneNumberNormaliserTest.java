package com.github.keycloak.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhoneNumberNormaliser")
class PhoneNumberNormaliserTest {

    @ParameterizedTest(name = "normalise({0}, {1}) = {2}")
    @CsvSource({
        // raw input,              country code, expected E.164
        "+8801712345678,           '',           +8801712345678",
        "01712345678,              880,          +8801712345678",
        "0044 7700 900000,         '',           +447700900000",
        "+1-800-555-0100,          '',           +18005550100",
        "+1 (800) 555-0100,        '',           +18005550100",
        "00447700900000,           '',           +447700900000",
        "01234567890,              1,            +11234567890",
        "+55 (11) 91234-5678,      55,           +5511912345678",
        "011912345678,             55,           +5511912345678",
    })
    void normalisesVariousFormats(String raw, String countryCode, String expected) {
        String cc = (countryCode == null || countryCode.trim().isEmpty()) ? null : countryCode.trim();
        assertEquals(expected, PhoneNumberNormaliser.normalise(raw.trim(), cc));
    }

    @Test
    @DisplayName("throws on blank input")
    void throwsOnBlank() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumberNormaliser.normalise("  "));
    }

    @Test
    @DisplayName("throws when non-digit characters remain after normalisation")
    void throwsOnInvalidChars() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumberNormaliser.normalise("abc-def-ghij"));
    }
}

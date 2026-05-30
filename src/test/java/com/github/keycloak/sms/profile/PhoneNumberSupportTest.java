package com.github.keycloak.sms.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PhoneNumberSupport.searchCandidates")
class PhoneNumberSupportTest {

  private static final String CC_BR = "55";

  @Test
  @DisplayName("bare national number yields normalised and country-prefixed candidates")
  void bareNationalNumber() {
    assertIterableEquals(
        List.of("+61986181809", "+5561986181809"),
        PhoneNumberSupport.searchCandidates("61986181809", CC_BR));
  }

  @Test
  @DisplayName("number already including country code yields single candidate")
  void numberWithCountryCodeDigits() {
    assertIterableEquals(
        List.of("+5561986181809"),
        PhoneNumberSupport.searchCandidates("5561986181809", CC_BR));
  }

  @Test
  @DisplayName("E.164 input yields single candidate")
  void e164Input() {
    assertIterableEquals(
        List.of("+5561986181809"),
        PhoneNumberSupport.searchCandidates("+5561986181809", CC_BR));
  }

  @Test
  @DisplayName("local 0-prefix yields single normalised candidate")
  void localZeroPrefix() {
    assertIterableEquals(
        List.of("+5511912345678"),
        PhoneNumberSupport.searchCandidates("011912345678", CC_BR));
  }

  @Test
  @DisplayName("throws when no candidate can be built")
  void throwsWhenInvalid() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PhoneNumberSupport.searchCandidates("abc-def", CC_BR));
  }

  @Test
  @DisplayName("without country code only normalise candidate is returned")
  void withoutCountryCode() {
    assertEquals(
        List.of("+61986181809"),
        List.copyOf(PhoneNumberSupport.searchCandidates("61986181809", null)));
  }
}

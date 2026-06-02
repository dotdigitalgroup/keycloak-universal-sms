package com.github.keycloak.sms.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  @DisplayName("searchKeys strips '+' from candidates")
  void searchKeysStripsPlus() {
    assertIterableEquals(
        List.of("61986181809", "5561986181809"),
        PhoneNumberSupport.searchKeys("61986181809", CC_BR));
  }

  @Test
  @DisplayName("searchKey canonicalises each stored mask/format to digits-only E.164")
  void searchKeyCanonicalises() {
    assertEquals("61986181809", PhoneNumberSupport.searchKey("(61) 98618-1809", CC_BR));
    assertEquals("61986181809", PhoneNumberSupport.searchKey("61986181809", CC_BR));
    assertEquals("5561986181809", PhoneNumberSupport.searchKey("+55 (61) 98618-1809", CC_BR));
    assertEquals("5561986181809", PhoneNumberSupport.searchKey("+5561986181809", CC_BR));
  }

  @Test
  @DisplayName("bare national input matches every stored format via the search index")
  void bareInputMatchesAllStoredFormats() {
    var keys = PhoneNumberSupport.searchKeys("61986181809", CC_BR);
    for (String stored : List.of(
        "(61) 98618-1809", "61986181809", "+55 (61) 98618-1809", "+5561986181809")) {
      assertTrue(
          keys.contains(PhoneNumberSupport.searchKey(stored, CC_BR)),
          "index for stored '" + stored + "' must be reachable from input keys");
    }
  }

  @Test
  @DisplayName("searchKey returns null for blank input")
  void searchKeyBlank() {
    assertEquals(null, PhoneNumberSupport.searchKey("   ", CC_BR));
  }
}

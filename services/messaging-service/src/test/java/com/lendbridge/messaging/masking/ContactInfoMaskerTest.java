package com.lendbridge.messaging.masking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContactInfoMaskerTest {

  @Test
  void masksEmailAddress() {
    assertThat(ContactInfoMasker.mask("reach me at jane.doe@example.com please"))
        .isEqualTo("reach me at [redacted] please");
  }

  @Test
  void masksNigerianLocalPhoneNumber() {
    assertThat(ContactInfoMasker.mask("call 08012345678 now")).isEqualTo("call [redacted] now");
  }

  @Test
  void masksE164PhoneNumber() {
    assertThat(ContactInfoMasker.mask("call +2348012345678 now")).isEqualTo("call [redacted] now");
  }

  @Test
  void masksBareAccountNumberDigitRun() {
    assertThat(ContactInfoMasker.mask("my account is 1234567890")).isEqualTo("my account is [redacted]");
  }

  @Test
  void masksUrl() {
    assertThat(ContactInfoMasker.mask("visit https://example.com/offer now")).isEqualTo("visit [redacted] now");
  }

  @Test
  void masksWwwUrlWithoutScheme() {
    assertThat(ContactInfoMasker.mask("visit www.example.com now")).isEqualTo("visit [redacted] now");
  }

  @Test
  void masksMultipleOccurrencesInOneMessage() {
    assertThat(ContactInfoMasker.mask("email a@b.com or call 08012345678"))
        .isEqualTo("email [redacted] or call [redacted]");
  }

  @Test
  void leavesOrdinaryTextUntouched() {
    String text = "Hi, when can you fund this loan? Thanks!";
    assertThat(ContactInfoMasker.mask(text)).isEqualTo(text);
  }

  @Test
  void leavesShortNumbersUntouched() {
    assertThat(ContactInfoMasker.mask("I'll pay 500 naira")).isEqualTo("I'll pay 500 naira");
  }

  @Test
  void handlesNullInput() {
    assertThat(ContactInfoMasker.mask(null)).isNull();
  }
}

package com.lendbridge.authentication.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumberTest {

  @Test
  void acceptsE164Format() {
    assertThat(PhoneNumber.of("+2348012345678").getValue()).isEqualTo("+2348012345678");
  }

  @Test
  void stripsSpaces() {
    assertThat(PhoneNumber.of("+234 801 234 5678").getValue()).isEqualTo("+2348012345678");
  }

  @Test
  void rejectsMissingPlus() {
    assertThatThrownBy(() -> PhoneNumber.of("2348012345678"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsTooShort() {
    assertThatThrownBy(() -> PhoneNumber.of("+1234567")).isInstanceOf(IllegalArgumentException.class);
  }
}

package com.lendbridge.authentication.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

  @Test
  void normalizesCaseAndWhitespace() {
    assertThat(Email.of("  Jane.Doe@Example.COM  ").getValue()).isEqualTo("jane.doe@example.com");
  }

  @Test
  void rejectsInvalidFormat() {
    assertThatThrownBy(() -> Email.of("not-an-email")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> Email.of(null)).isInstanceOf(IllegalArgumentException.class);
  }
}

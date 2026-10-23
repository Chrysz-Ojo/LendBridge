package com.lendbridge.authentication.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordTest {

  @Test
  void acceptsAPasswordMeetingAllRules() {
    Password password = Password.of("Str0ng!Pass");
    assertThat(password.getValue()).isEqualTo("Str0ng!Pass");
  }

  @Test
  void acceptsExactlyTenCharacters() {
    assertThat(Password.of("Ab1!Ab1!Ab").getValue()).hasSize(10);
  }

  @Test
  void rejectsNineCharacters() {
    assertThatThrownBy(() -> Password.of("Ab1!Ab1!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("10 characters");
  }

  @Test
  void rejectsMissingUppercase() {
    assertThatThrownBy(() -> Password.of("str0ng!pass"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uppercase");
  }

  @Test
  void rejectsMissingLowercase() {
    assertThatThrownBy(() -> Password.of("STR0NG!PASS"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lowercase");
  }

  @Test
  void rejectsMissingDigit() {
    assertThatThrownBy(() -> Password.of("Strong!Pass"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("digit");
  }

  @Test
  void rejectsMissingSymbol() {
    assertThatThrownBy(() -> Password.of("Str0ngPass0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("symbol");
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> Password.of(null)).isInstanceOf(IllegalArgumentException.class);
  }
}

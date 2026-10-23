package com.lendbridge.authentication.model;

import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Immutable value object wrapping a plaintext password just long enough to validate it before it
 * is hashed with BCrypt; the raw value is never persisted.
 *
 * <p>Complexity rule: at least 10 characters, with at least one uppercase letter, one lowercase
 * letter, one digit and one symbol.
 */
@Getter
@EqualsAndHashCode
public final class Password {

  private static final int MIN_LENGTH = 10;
  private static final Pattern UPPER = Pattern.compile("[A-Z]");
  private static final Pattern LOWER = Pattern.compile("[a-z]");
  private static final Pattern DIGIT = Pattern.compile("\\d");
  private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

  private final String value;

  private Password(String value) {
    this.value = value;
  }

  public static Password of(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Password must not be null");
    }
    if (raw.length() < MIN_LENGTH) {
      throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
    }
    if (!UPPER.matcher(raw).find()) {
      throw new IllegalArgumentException("Password must contain at least one uppercase letter");
    }
    if (!LOWER.matcher(raw).find()) {
      throw new IllegalArgumentException("Password must contain at least one lowercase letter");
    }
    if (!DIGIT.matcher(raw).find()) {
      throw new IllegalArgumentException("Password must contain at least one digit");
    }
    if (!SYMBOL.matcher(raw).find()) {
      throw new IllegalArgumentException("Password must contain at least one symbol");
    }
    return new Password(raw);
  }

  @Override
  public String toString() {
    return "Password[REDACTED]";
  }
}

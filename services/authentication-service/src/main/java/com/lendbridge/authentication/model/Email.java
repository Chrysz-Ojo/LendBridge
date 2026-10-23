package com.lendbridge.authentication.model;

import java.util.Locale;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Immutable value object: a validated, normalized (trimmed, lower-cased) email address. */
@Getter
@EqualsAndHashCode
public final class Email {

  private static final Pattern PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  private final String value;

  private Email(String value) {
    this.value = value;
  }

  public static Email of(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Email must not be null");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (!PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Email is not valid: " + raw);
    }
    return new Email(normalized);
  }

  @Override
  public String toString() {
    return value;
  }
}

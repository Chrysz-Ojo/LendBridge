package com.lendbridge.authentication.model;

import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Immutable value object: a validated, normalized E.164 phone number (e.g. +2348012345678). */
@Getter
@EqualsAndHashCode
public final class PhoneNumber {

  private static final Pattern PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

  private final String value;

  private PhoneNumber(String value) {
    this.value = value;
  }

  public static PhoneNumber of(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Phone number must not be null");
    }
    String normalized = raw.trim().replace(" ", "");
    if (!PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Phone number is not a valid E.164 number: " + raw);
    }
    return new PhoneNumber(normalized);
  }

  @Override
  public String toString() {
    return value;
  }
}

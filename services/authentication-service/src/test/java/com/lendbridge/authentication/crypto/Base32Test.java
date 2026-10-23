package com.lendbridge.authentication.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Base32Test {

  // RFC 4648 section 10 test vectors.
  static Stream<org.junit.jupiter.params.provider.Arguments> rfc4648Vectors() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of("", ""),
        org.junit.jupiter.params.provider.Arguments.of("f", "MY======"),
        org.junit.jupiter.params.provider.Arguments.of("fo", "MZXQ===="),
        org.junit.jupiter.params.provider.Arguments.of("foo", "MZXW6==="),
        org.junit.jupiter.params.provider.Arguments.of("foob", "MZXW6YQ="),
        org.junit.jupiter.params.provider.Arguments.of("fooba", "MZXW6YTB"),
        org.junit.jupiter.params.provider.Arguments.of("foobar", "MZXW6YTBOI======"));
  }

  @ParameterizedTest
  @MethodSource("rfc4648Vectors")
  void encodesPerRfc4648(String input, String expected) {
    assertThat(Base32.encode(input.getBytes(StandardCharsets.US_ASCII))).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("rfc4648Vectors")
  void decodesPerRfc4648(String expected, String encoded) {
    assertThat(new String(Base32.decode(encoded), StandardCharsets.US_ASCII)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("rfc4648Vectors")
  void roundTrips(String input, String ignored) {
    byte[] original = input.getBytes(StandardCharsets.US_ASCII);
    assertThat(Base32.decode(Base32.encode(original))).isEqualTo(original);
  }
}

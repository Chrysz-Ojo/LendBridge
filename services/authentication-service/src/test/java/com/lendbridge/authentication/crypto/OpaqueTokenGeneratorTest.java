package com.lendbridge.authentication.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpaqueTokenGeneratorTest {

  private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();

  @Test
  void generatesHighEntropyUniqueTokens() {
    String first = generator.generateRaw();
    String second = generator.generateRaw();
    assertThat(first).isNotEqualTo(second);
    assertThat(first.length()).isGreaterThanOrEqualTo(40);
  }

  @Test
  void hashIsDeterministic() {
    String token = generator.generateRaw();
    assertThat(generator.hash(token)).isEqualTo(generator.hash(token));
  }

  @Test
  void hashDoesNotRevealRawToken() {
    String token = generator.generateRaw();
    assertThat(generator.hash(token)).isNotEqualTo(token);
  }
}

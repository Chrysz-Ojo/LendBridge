package com.lendbridge.kyc.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityHasherTest {

  private final IdentityHasher hasher = new IdentityHasher("test-pepper");

  @Test
  void hash_isDeterministicForTheSameInput() {
    assertThat(hasher.hash("12345678901")).isEqualTo(hasher.hash("12345678901"));
  }

  @Test
  void hash_differsForDifferentInput() {
    assertThat(hasher.hash("12345678901")).isNotEqualTo(hasher.hash("10987654321"));
  }

  @Test
  void hash_differsAcrossDifferentPeppers() {
    IdentityHasher other = new IdentityHasher("different-pepper");
    assertThat(hasher.hash("12345678901")).isNotEqualTo(other.hash("12345678901"));
  }
}

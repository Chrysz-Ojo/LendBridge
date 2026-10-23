package com.lendbridge.authentication.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacPhoneHasherTest {

  @Test
  void isDeterministicForSameInputAndPepper() {
    HmacPhoneHasher hasher = new HmacPhoneHasher("pepper-1");
    assertThat(hasher.hash("+2348012345678")).isEqualTo(hasher.hash("+2348012345678"));
  }

  @Test
  void differentPeppersProduceDifferentHashes() {
    HmacPhoneHasher first = new HmacPhoneHasher("pepper-1");
    HmacPhoneHasher second = new HmacPhoneHasher("pepper-2");
    assertThat(first.hash("+2348012345678")).isNotEqualTo(second.hash("+2348012345678"));
  }

  @Test
  void differentPhonesProduceDifferentHashes() {
    HmacPhoneHasher hasher = new HmacPhoneHasher("pepper-1");
    assertThat(hasher.hash("+2348012345678")).isNotEqualTo(hasher.hash("+2348087654321"));
  }
}

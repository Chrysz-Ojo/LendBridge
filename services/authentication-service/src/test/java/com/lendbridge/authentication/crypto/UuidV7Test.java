package com.lendbridge.authentication.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void hasVersion7AndIetfVariant() {
    UUID uuid = UuidV7.generate();
    assertThat(uuid.version()).isEqualTo(7);
    assertThat(uuid.variant()).isEqualTo(2);
  }

  @Test
  void isTimeOrderedAcrossSuccessiveCalls() throws InterruptedException {
    // Ordering is only guaranteed to millisecond granularity; random bits within the same
    // millisecond are not ordered, so force a gap before comparing.
    UUID first = UuidV7.generate();
    Thread.sleep(2);
    UUID second = UuidV7.generate();
    assertThat(first.toString().compareTo(second.toString())).isLessThan(0);
  }

  @Test
  void generatesUniqueValues() {
    UUID first = UuidV7.generate();
    UUID second = UuidV7.generate();
    assertThat(first).isNotEqualTo(second);
  }
}

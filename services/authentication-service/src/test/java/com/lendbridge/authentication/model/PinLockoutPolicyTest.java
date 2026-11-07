package com.lendbridge.authentication.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PinLockoutPolicyTest {

  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void doesNotLockAfterFourthFailure() {
    assertThat(PinLockoutPolicy.lockedUntilAfterFailure(4, now)).isNull();
  }

  @Test
  void locksAfterFifthFailure() {
    Instant lockedUntil = PinLockoutPolicy.lockedUntilAfterFailure(5, now);
    assertThat(lockedUntil).isEqualTo(now.plusSeconds(15 * 60));
  }

  @Test
  void staysLockedOnFurtherFailuresWhileAlreadyAtOrAboveThreshold() {
    assertThat(PinLockoutPolicy.lockedUntilAfterFailure(6, now)).isEqualTo(now.plusSeconds(15 * 60));
  }
}

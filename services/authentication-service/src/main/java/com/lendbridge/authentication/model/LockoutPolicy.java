package com.lendbridge.authentication.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 5 consecutive failed login attempts lock the account for 15 minutes. */
public final class LockoutPolicy {

  public static final int MAX_FAILED_ATTEMPTS = 5;
  public static final long LOCK_DURATION_MINUTES = 15;

  private LockoutPolicy() {}

  /** Returns the lockedUntil instant to apply after this failure, or null if not yet locked. */
  public static Instant lockedUntilAfterFailure(int failedAttemptsAfterThisOne, Instant now) {
    if (failedAttemptsAfterThisOne >= MAX_FAILED_ATTEMPTS) {
      return now.plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES);
    }
    return null;
  }
}

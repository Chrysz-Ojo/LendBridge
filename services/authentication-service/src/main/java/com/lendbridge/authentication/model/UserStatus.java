package com.lendbridge.authentication.model;

/**
 * Account lifecycle status. Transient login lockout (5 failed attempts -> 15 minutes) is tracked
 * separately via {@code app_user.locked_until} and does not change this status; SUSPENDED is
 * reserved for an explicit admin action.
 */
public enum UserStatus {
  PENDING_VERIFICATION,
  ACTIVE,
  SUSPENDED
}

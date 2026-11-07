package com.lendbridge.authentication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A separate, lightweight credential from the login password (PRD E12), used to confirm
 * money-moving actions. Lockout is tracked independently of {@code app_user}'s own login lockout
 * — a locked PIN blocks PIN-gated actions only, never login. */
@Entity
@Table(name = "transaction_pin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionPin {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "pin_hash", nullable = false)
  private String pinHash;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public boolean isCurrentlyLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }
}

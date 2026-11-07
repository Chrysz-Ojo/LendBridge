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

/** Forensic audit trail only (E12-S5); the lockout decision is driven by {@code transaction_pin}'s
 * own counter, same split as {@link LoginAttempt} versus {@code app_user}. */
@Entity
@Table(name = "pin_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinAttempt {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private boolean success;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}

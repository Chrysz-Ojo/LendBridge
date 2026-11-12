package com.lendbridge.reporting.model;

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

/** One row per {@code user.registered} event — backs the user-growth report. */
@Entity
@Table(name = "user_signup_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSignupFact {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}

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

/** Forensic audit trail only; the lockout decision is driven by {@code app_user}'s own counter. */
@Entity
@Table(name = "login_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

  @Id private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false)
  private String identifier;

  @Column(nullable = false)
  private boolean success;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}

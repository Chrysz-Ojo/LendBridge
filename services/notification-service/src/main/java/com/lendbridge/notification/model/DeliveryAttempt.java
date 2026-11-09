package com.lendbridge.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One record per actual send attempt (not per skip). {@code notificationId} is a plain column,
 * not a JPA association, matching the rest of the codebase's style (e.g. investment-service's
 * {@code Commitment.loanId}). */
@Entity
@Table(name = "delivery_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAttempt {

  @Id
  private UUID id;

  @Column(name = "notification_id", nullable = false)
  private UUID notificationId;

  @Column(nullable = false)
  private String provider;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationStatus status;

  private String error;

  @Column(name = "attempted_at", nullable = false)
  private Instant attemptedAt;
}

package com.lendbridge.notification.model;

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

/** A user's override of the blanket {@link ChannelSettings} for one specific event type. Absence
 * of a row for (userId, eventType) means the blanket channel settings apply unchanged. */
@Entity
@Table(name = "event_preference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventPreference {

  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled;

  @Column(name = "sms_enabled", nullable = false)
  private boolean smsEnabled;

  @Column(name = "in_app_enabled", nullable = false)
  private boolean inAppEnabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

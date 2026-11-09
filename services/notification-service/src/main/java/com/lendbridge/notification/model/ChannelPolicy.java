package com.lendbridge.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Platform-wide, admin-controlled override of which channels are used for an event type — the
 * cost-control lever (SMS costs money per message; email/in-app don't). Absence of a row for an
 * event type means no restriction; this takes precedence over every per-user setting. */
@Entity
@Table(name = "channel_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelPolicy {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "event_type")
  private NotificationType eventType;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled;

  @Column(name = "sms_enabled", nullable = false)
  private boolean smsEnabled;

  @Column(name = "in_app_enabled", nullable = false)
  private boolean inAppEnabled;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static ChannelPolicy defaults(NotificationType eventType) {
    return ChannelPolicy.builder()
        .eventType(eventType)
        .emailEnabled(true)
        .smsEnabled(true)
        .inAppEnabled(true)
        .build();
  }
}

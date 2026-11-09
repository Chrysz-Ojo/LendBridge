package com.lendbridge.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-user channel preferences (PRD E9-S3: "user controls marketing opt-in separately from
 * transactional messages"). Absence of a row means the column defaults apply: email and SMS
 * enabled, marketing opt-in off. */
@Entity
@Table(name = "channel_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelSettings {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled;

  @Column(name = "sms_enabled", nullable = false)
  private boolean smsEnabled;

  @Column(name = "marketing_opt_in", nullable = false)
  private boolean marketingOptIn;

  public static ChannelSettings defaults(UUID userId) {
    return ChannelSettings.builder()
        .userId(userId)
        .emailEnabled(true)
        .smsEnabled(true)
        .marketingOptIn(false)
        .build();
  }
}

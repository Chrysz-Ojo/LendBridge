package com.lendbridge.notification.outbox;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSentPayload {
  private UUID notificationId;
  private UUID userId;
  private String channel;
  private String eventType;
  private String status;
  private long occurredAtEpochMillis;
}

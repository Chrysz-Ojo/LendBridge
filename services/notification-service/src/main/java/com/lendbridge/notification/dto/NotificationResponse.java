package com.lendbridge.notification.dto;

import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.NotificationStatus;
import java.time.Instant;
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
public class NotificationResponse {
  private UUID id;
  private String eventType;
  private Channel channel;
  private String subject;
  private String body;
  private NotificationStatus status;
  private Instant createdAt;
  private Instant readAt;
}

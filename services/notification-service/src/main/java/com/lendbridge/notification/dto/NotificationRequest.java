package com.lendbridge.notification.dto;

import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.NotificationType;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A request to notify one user on one channel. {@code recipient} is optional: if null, the email
 * or phone is looked up from the stored contact. {@code subject} and {@code body} are optional: if
 * null, the template for (type, channel) is used.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {
  private UUID userId;
  private Channel channel;
  private NotificationType type;
  private String recipient;
  private String subject;
  private String body;
  private Map<String, String> variables;

}

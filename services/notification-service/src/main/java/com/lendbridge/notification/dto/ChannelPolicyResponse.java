package com.lendbridge.notification.dto;

import java.time.Instant;
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
public class ChannelPolicyResponse {
  private String eventType;
  private boolean emailEnabled;
  private boolean smsEnabled;
  private boolean inAppEnabled;
  private Instant updatedAt;
}

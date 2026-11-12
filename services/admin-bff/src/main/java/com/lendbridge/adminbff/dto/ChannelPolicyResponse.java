package com.lendbridge.adminbff.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelPolicyResponse {
  private String eventType;
  private boolean emailEnabled;
  private boolean smsEnabled;
  private boolean inAppEnabled;
  private Instant updatedAt;
}

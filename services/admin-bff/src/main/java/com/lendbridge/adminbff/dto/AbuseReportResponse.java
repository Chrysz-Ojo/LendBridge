package com.lendbridge.adminbff.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbuseReportResponse {
  private UUID id;
  private UUID conversationId;
  private UUID reporterUserId;
  private String reason;
  private String status;
  private Instant createdAt;
}

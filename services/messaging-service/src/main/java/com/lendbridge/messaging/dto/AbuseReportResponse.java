package com.lendbridge.messaging.dto;

import com.lendbridge.messaging.model.AbuseReportStatus;
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
  private AbuseReportStatus status;
  private Instant createdAt;
}

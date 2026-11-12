package com.lendbridge.audit.dto;

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
public class AuditRecordResponse {
  private UUID id;
  private String topic;
  private String aggregateKey;
  private String payload;
  private Instant occurredAt;
  private Instant recordedAt;
}

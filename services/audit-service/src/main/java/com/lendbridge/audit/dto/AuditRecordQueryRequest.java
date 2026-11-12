package com.lendbridge.audit.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecordQueryRequest {
  private String topic;
  private String aggregateKey;
  private Instant from;
  private Instant to;
  private int limit;
}

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
public class ReconciliationRunResponse {
  private UUID id;
  private String provider;
  private Instant periodStart;
  private Instant periodEnd;
  private String status;
  private int totalProviderRecords;
  private int totalMatched;
  private int totalBreaks;
  private Instant startedAt;
  private Instant completedAt;
}

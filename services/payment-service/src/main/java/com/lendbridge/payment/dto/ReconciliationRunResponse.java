package com.lendbridge.payment.dto;

import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.model.ReconciliationStatus;
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
public class ReconciliationRunResponse {
  private UUID id;
  private ProviderName provider;
  private Instant periodStart;
  private Instant periodEnd;
  private ReconciliationStatus status;
  private int totalProviderRecords;
  private int totalMatched;
  private int totalBreaks;
  private Instant startedAt;
  private Instant completedAt;
}

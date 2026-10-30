package com.lendbridge.investment.dto;

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
public class PreferenceResponse {
  private UUID id;
  private long amountPerLoanKobo;
  private long totalBudgetKobo;
  private int minRateBps;
  private int minTenorMonths;
  private int maxTenorMonths;
  private Instant createdAt;
  private Instant updatedAt;
}

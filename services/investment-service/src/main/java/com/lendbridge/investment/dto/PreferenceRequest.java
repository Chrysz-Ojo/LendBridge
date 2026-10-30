package com.lendbridge.investment.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceRequest {
  @Positive private long amountPerLoanKobo;
  @Positive private long totalBudgetKobo;
  @PositiveOrZero private int minRateBps;
  @Positive private int minTenorMonths;
  @Positive private int maxTenorMonths;
}

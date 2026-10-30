package com.lendbridge.loan.service.impl;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Inputs to {@link RepaymentScheduleCalculator#compute(ScheduleCalculationRequest)}, package-private
 * for the same reason as kyc-service's {@code TrustTierCalculationRequest}. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ScheduleCalculationRequest {
  private long amountKobo;
  private int tenorMonths;
  private int interestRateBps;
  private LocalDate startDate;
}

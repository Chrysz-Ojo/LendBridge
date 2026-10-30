package com.lendbridge.loan.service.impl;

import com.lendbridge.loan.dto.InstalmentResponse;
import com.lendbridge.loan.dto.ScheduleResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard amortisation-formula schedule, pure and stateless (same shape as kyc-service's
 * {@code TrustTierCalculator}). Internal math runs in {@code BigDecimal} with
 * {@link RoundingMode#HALF_EVEN} (banker's rounding, per the PRD's "Key business rules"); every
 * instalment is rounded to the nearest kobo except the last, whose principal portion is forced to
 * absorb whatever is left of the outstanding balance exactly — this is what guarantees the
 * schedule's total principal sums to exactly the loan amount with zero leftover, per the PRD's
 * "rounding remainder goes to final instalment" rule.
 */
final class RepaymentScheduleCalculator {

  private RepaymentScheduleCalculator() {}

  static ScheduleResponse compute(ScheduleCalculationRequest request) {
    BigDecimal monthlyRate =
        BigDecimal.valueOf(request.getInterestRateBps())
            .divide(BigDecimal.valueOf(10_000), 12, RoundingMode.HALF_EVEN)
            .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_EVEN);

    BigDecimal principal = BigDecimal.valueOf(request.getAmountKobo());
    int tenorMonths = request.getTenorMonths();
    BigDecimal instalmentAmount = regularInstalmentAmount(principal, monthlyRate, tenorMonths);

    List<InstalmentResponse> instalments = new ArrayList<>(tenorMonths);
    BigDecimal outstandingPrincipal = principal;
    long totalPrincipalKobo = 0;
    long totalInterestKobo = 0;

    for (int month = 1; month <= tenorMonths; month++) {
      long interestKobo = roundToKobo(outstandingPrincipal.multiply(monthlyRate));
      long principalKobo;
      long instalmentTotalKobo;

      if (month == tenorMonths) {
        principalKobo = roundToKobo(outstandingPrincipal);
        instalmentTotalKobo = principalKobo + interestKobo;
      } else {
        long instalmentKobo = roundToKobo(instalmentAmount);
        principalKobo = instalmentKobo - interestKobo;
        instalmentTotalKobo = instalmentKobo;
      }

      outstandingPrincipal = outstandingPrincipal.subtract(BigDecimal.valueOf(principalKobo));
      totalPrincipalKobo += principalKobo;
      totalInterestKobo += interestKobo;

      instalments.add(
          InstalmentResponse.builder()
              .instalmentNumber(month)
              .dueDate(request.getStartDate().plusMonths(month))
              .principalKobo(principalKobo)
              .interestKobo(interestKobo)
              .totalKobo(instalmentTotalKobo)
              .outstandingBalanceKobo(Math.max(outstandingPrincipal.longValue(), 0))
              .build());
    }

    return ScheduleResponse.builder()
        .totalPrincipalKobo(totalPrincipalKobo)
        .totalInterestKobo(totalInterestKobo)
        .totalRepaymentKobo(totalPrincipalKobo + totalInterestKobo)
        .instalments(instalments)
        .build();
  }

  private static BigDecimal regularInstalmentAmount(BigDecimal principal, BigDecimal monthlyRate, int tenorMonths) {
    if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
      return principal.divide(BigDecimal.valueOf(tenorMonths), 12, RoundingMode.HALF_EVEN);
    }
    BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
    BigDecimal onePlusRPowN = onePlusR.pow(tenorMonths);
    BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN);
    BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);
    return numerator.divide(denominator, 12, RoundingMode.HALF_EVEN);
  }

  private static long roundToKobo(BigDecimal value) {
    return value.setScale(0, RoundingMode.HALF_EVEN).longValueExact();
  }
}

package com.lendbridge.loan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.loan.dto.InstalmentResponse;
import com.lendbridge.loan.dto.ScheduleResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RepaymentScheduleCalculatorTest {

  @Test
  void compute_amortisingLoan_principalSumsExactlyToTheOriginalAmount() {
    ScheduleResponse schedule =
        RepaymentScheduleCalculator.compute(
            ScheduleCalculationRequest.builder()
                .amountKobo(1_200_000L)
                .tenorMonths(12)
                .interestRateBps(1_200) // 12% annual
                .startDate(LocalDate.of(2026, 1, 1))
                .build());

    assertThat(schedule.getInstalments()).hasSize(12);
    assertThat(schedule.getTotalPrincipalKobo()).isEqualTo(1_200_000L);
    long summedPrincipal = schedule.getInstalments().stream().mapToLong(InstalmentResponse::getPrincipalKobo).sum();
    assertThat(summedPrincipal).isEqualTo(1_200_000L);
    assertThat(schedule.getTotalRepaymentKobo()).isEqualTo(schedule.getTotalPrincipalKobo() + schedule.getTotalInterestKobo());
    assertThat(schedule.getTotalInterestKobo()).isPositive();
  }

  @Test
  void compute_amortisingLoan_outstandingBalanceReachesExactlyZeroAfterTheFinalInstalment() {
    ScheduleResponse schedule =
        RepaymentScheduleCalculator.compute(
            ScheduleCalculationRequest.builder()
                .amountKobo(5_000_000L)
                .tenorMonths(6)
                .interestRateBps(1_850)
                .startDate(LocalDate.of(2026, 3, 15))
                .build());

    InstalmentResponse last = schedule.getInstalments().get(schedule.getInstalments().size() - 1);
    assertThat(last.getOutstandingBalanceKobo()).isZero();
  }

  @Test
  void compute_amortisingLoan_eachInstalmentTotalIsPrincipalPlusInterest() {
    ScheduleResponse schedule =
        RepaymentScheduleCalculator.compute(
            ScheduleCalculationRequest.builder()
                .amountKobo(2_500_000L)
                .tenorMonths(9)
                .interestRateBps(2_400)
                .startDate(LocalDate.of(2026, 6, 1))
                .build());

    for (InstalmentResponse instalment : schedule.getInstalments()) {
      assertThat(instalment.getTotalKobo()).isEqualTo(instalment.getPrincipalKobo() + instalment.getInterestKobo());
    }
  }

  @Test
  void compute_amortisingLoan_dueDatesStepOneMonthApartFromTheStartDate() {
    LocalDate start = LocalDate.of(2026, 1, 31);
    ScheduleResponse schedule =
        RepaymentScheduleCalculator.compute(
            ScheduleCalculationRequest.builder()
                .amountKobo(1_000_000L)
                .tenorMonths(3)
                .interestRateBps(1_000)
                .startDate(start)
                .build());

    assertThat(schedule.getInstalments().get(0).getDueDate()).isEqualTo(start.plusMonths(1));
    assertThat(schedule.getInstalments().get(1).getDueDate()).isEqualTo(start.plusMonths(2));
    assertThat(schedule.getInstalments().get(2).getDueDate()).isEqualTo(start.plusMonths(3));
  }

  @Test
  void compute_zeroInterestRate_splitsPrincipalEvenlyWithNoInterest() {
    ScheduleResponse schedule =
        RepaymentScheduleCalculator.compute(
            ScheduleCalculationRequest.builder()
                .amountKobo(1_200_000L)
                .tenorMonths(12)
                .interestRateBps(0)
                .startDate(LocalDate.of(2026, 1, 1))
                .build());

    assertThat(schedule.getTotalInterestKobo()).isZero();
    assertThat(schedule.getTotalPrincipalKobo()).isEqualTo(1_200_000L);
    for (InstalmentResponse instalment : schedule.getInstalments()) {
      assertThat(instalment.getInterestKobo()).isZero();
      assertThat(instalment.getPrincipalKobo()).isCloseTo(100_000L, org.assertj.core.data.Offset.offset(1L));
    }
  }

  @Test
  void compute_singleMonthTenor_isJustPrincipalPlusOneMonthsInterest() {
    ScheduleResponse schedule =
        RepaymentScheduleCalculator.compute(
            ScheduleCalculationRequest.builder()
                .amountKobo(1_000_000L)
                .tenorMonths(1)
                .interestRateBps(1_200)
                .startDate(LocalDate.of(2026, 1, 1))
                .build());

    assertThat(schedule.getInstalments()).hasSize(1);
    InstalmentResponse only = schedule.getInstalments().get(0);
    assertThat(only.getPrincipalKobo()).isEqualTo(1_000_000L);
    assertThat(only.getOutstandingBalanceKobo()).isZero();
    // 12% annual / 12 = 1% for the one month: 1,000,000 * 0.01 = 10,000
    assertThat(only.getInterestKobo()).isEqualTo(10_000L);
  }
}

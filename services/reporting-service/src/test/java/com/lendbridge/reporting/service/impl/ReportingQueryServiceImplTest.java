package com.lendbridge.reporting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lendbridge.reporting.dto.ReportQueryRequest;
import com.lendbridge.reporting.dto.ReportTable;
import com.lendbridge.reporting.model.InvestmentActivityType;
import com.lendbridge.reporting.model.KycDecisionType;
import com.lendbridge.reporting.model.LoanPipelineStatus;
import com.lendbridge.reporting.repository.InvestmentActivityFactRepository;
import com.lendbridge.reporting.repository.KycDecisionFactRepository;
import com.lendbridge.reporting.repository.LoanPipelineFactRepository;
import com.lendbridge.reporting.repository.RiskGradeFactRepository;
import com.lendbridge.reporting.repository.UserSignupFactRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportingQueryServiceImplTest {

  private UserSignupFactRepository userSignupFactRepository;
  private LoanPipelineFactRepository loanPipelineFactRepository;
  private RiskGradeFactRepository riskGradeFactRepository;
  private InvestmentActivityFactRepository investmentActivityFactRepository;
  private KycDecisionFactRepository kycDecisionFactRepository;
  private ReportingQueryServiceImpl service;

  @BeforeEach
  void setUp() {
    userSignupFactRepository = mock(UserSignupFactRepository.class);
    loanPipelineFactRepository = mock(LoanPipelineFactRepository.class);
    riskGradeFactRepository = mock(RiskGradeFactRepository.class);
    investmentActivityFactRepository = mock(InvestmentActivityFactRepository.class);
    kycDecisionFactRepository = mock(KycDecisionFactRepository.class);
    service =
        new ReportingQueryServiceImpl(
            userSignupFactRepository,
            loanPipelineFactRepository,
            riskGradeFactRepository,
            investmentActivityFactRepository,
            kycDecisionFactRepository);
  }

  @Test
  void userGrowth_groupsOccurrencesByCalendarDay() {
    Instant day1a = Instant.parse("2026-01-01T01:00:00Z");
    Instant day1b = Instant.parse("2026-01-01T23:00:00Z");
    Instant day2 = Instant.parse("2026-01-02T05:00:00Z");
    when(userSignupFactRepository.findOccurredAtInRange(any(), any())).thenReturn(List.of(day1a, day1b, day2));

    ReportTable table = service.userGrowth(ReportQueryRequest.builder().build());

    assertThat(table.getHeaders()).containsExactly("day", "signups");
    assertThat(table.getRows()).containsExactly(List.of("2026-01-01", "2"), List.of("2026-01-02", "1"));
  }

  @Test
  void userGrowth_noOccurrences_returnsEmptyRows() {
    when(userSignupFactRepository.findOccurredAtInRange(any(), any())).thenReturn(List.of());

    ReportTable table = service.userGrowth(ReportQueryRequest.builder().build());

    assertThat(table.getRows()).isEmpty();
  }

  @Test
  void loanPipeline_mapsGroupedRowsToTable() {
    when(loanPipelineFactRepository.countByStatus(any(), any()))
        .thenReturn(
            List.of(
                new Object[] {LoanPipelineStatus.LISTED, 3L, 900_000L},
                new Object[] {LoanPipelineStatus.SUBMITTED, 5L, 1_500_000L}));

    ReportTable table = service.loanPipeline(ReportQueryRequest.builder().build());

    assertThat(table.getHeaders()).containsExactly("status", "count", "totalAmountKobo");
    assertThat(table.getRows())
        .containsExactly(
            List.of("LISTED", "3", "900000"), List.of("SUBMITTED", "5", "1500000"));
  }

  @Test
  void riskGrades_mapsGroupedRowsToTable() {
    when(riskGradeFactRepository.countByGrade(any(), any()))
        .thenReturn(List.of(new Object[] {"A", 2L}, new Object[] {"E", 1L}));

    ReportTable table = service.riskGrades(ReportQueryRequest.builder().build());

    assertThat(table.getHeaders()).containsExactly("grade", "count");
    assertThat(table.getRows()).containsExactly(List.of("A", "2"), List.of("E", "1"));
  }

  @Test
  void investmentActivity_mapsGroupedRowsToTable() {
    when(investmentActivityFactRepository.countByActivityType(any(), any()))
        .thenReturn(List.<Object[]>of(new Object[] {InvestmentActivityType.COMMITTED, 4L, 2_000_000L}));

    ReportTable table = service.investmentActivity(ReportQueryRequest.builder().build());

    assertThat(table.getHeaders()).containsExactly("activityType", "count", "totalAmountKobo");
    assertThat(table.getRows()).containsExactly(List.of("COMMITTED", "4", "2000000"));
  }

  @Test
  void kycApprovalRate_mapsGroupedRowsToTable() {
    when(kycDecisionFactRepository.countByDecision(any(), any()))
        .thenReturn(
            List.of(new Object[] {KycDecisionType.REJECTED, 1L}, new Object[] {KycDecisionType.VERIFIED, 9L}));

    ReportTable table = service.kycApprovalRate(ReportQueryRequest.builder().build());

    assertThat(table.getHeaders()).containsExactly("decision", "count");
    assertThat(table.getRows()).containsExactly(List.of("REJECTED", "1"), List.of("VERIFIED", "9"));
  }

  @Test
  void userGrowth_respectsExplicitDateRange() {
    Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
    Instant to = Instant.now();
    when(userSignupFactRepository.findOccurredAtInRange(from, to)).thenReturn(List.of());

    service.userGrowth(ReportQueryRequest.builder().from(from).to(to).build());

    org.mockito.Mockito.verify(userSignupFactRepository).findOccurredAtInRange(from, to);
  }
}

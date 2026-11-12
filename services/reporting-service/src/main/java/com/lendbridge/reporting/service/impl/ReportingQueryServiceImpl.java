package com.lendbridge.reporting.service.impl;

import com.lendbridge.reporting.dto.ReportQueryRequest;
import com.lendbridge.reporting.dto.ReportTable;
import com.lendbridge.reporting.repository.InvestmentActivityFactRepository;
import com.lendbridge.reporting.repository.KycDecisionFactRepository;
import com.lendbridge.reporting.repository.LoanPipelineFactRepository;
import com.lendbridge.reporting.repository.RiskGradeFactRepository;
import com.lendbridge.reporting.repository.UserSignupFactRepository;
import com.lendbridge.reporting.service.ReportingQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingQueryServiceImpl implements ReportingQueryService {

  private final UserSignupFactRepository userSignupFactRepository;
  private final LoanPipelineFactRepository loanPipelineFactRepository;
  private final RiskGradeFactRepository riskGradeFactRepository;
  private final InvestmentActivityFactRepository investmentActivityFactRepository;
  private final KycDecisionFactRepository kycDecisionFactRepository;

  public ReportingQueryServiceImpl(
      UserSignupFactRepository userSignupFactRepository,
      LoanPipelineFactRepository loanPipelineFactRepository,
      RiskGradeFactRepository riskGradeFactRepository,
      InvestmentActivityFactRepository investmentActivityFactRepository,
      KycDecisionFactRepository kycDecisionFactRepository) {
    this.userSignupFactRepository = userSignupFactRepository;
    this.loanPipelineFactRepository = loanPipelineFactRepository;
    this.riskGradeFactRepository = riskGradeFactRepository;
    this.investmentActivityFactRepository = investmentActivityFactRepository;
    this.kycDecisionFactRepository = kycDecisionFactRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public ReportTable userGrowth(ReportQueryRequest request) {
    List<Instant> occurrences = userSignupFactRepository.findOccurredAtInRange(request.getFrom(), request.getTo());
    Map<LocalDate, Long> byDay = new TreeMap<>();
    for (Instant occurredAt : occurrences) {
      LocalDate day = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
      byDay.merge(day, 1L, Long::sum);
    }
    return ReportTable.builder()
        .headers(List.of("day", "signups"))
        .rows(byDay.entrySet().stream().map(e -> List.of(e.getKey().toString(), String.valueOf(e.getValue()))).toList())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ReportTable loanPipeline(ReportQueryRequest request) {
    List<Object[]> rows = loanPipelineFactRepository.countByStatus(request.getFrom(), request.getTo());
    return ReportTable.builder()
        .headers(List.of("status", "count", "totalAmountKobo"))
        .rows(
            rows.stream()
                .map(row -> List.of(String.valueOf(row[0]), String.valueOf(row[1]), String.valueOf(row[2])))
                .toList())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ReportTable riskGrades(ReportQueryRequest request) {
    List<Object[]> rows = riskGradeFactRepository.countByGrade(request.getFrom(), request.getTo());
    return ReportTable.builder()
        .headers(List.of("grade", "count"))
        .rows(rows.stream().map(row -> List.of(String.valueOf(row[0]), String.valueOf(row[1]))).toList())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ReportTable investmentActivity(ReportQueryRequest request) {
    List<Object[]> rows =
        investmentActivityFactRepository.countByActivityType(request.getFrom(), request.getTo());
    return ReportTable.builder()
        .headers(List.of("activityType", "count", "totalAmountKobo"))
        .rows(
            rows.stream()
                .map(row -> List.of(String.valueOf(row[0]), String.valueOf(row[1]), String.valueOf(row[2])))
                .toList())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ReportTable kycApprovalRate(ReportQueryRequest request) {
    List<Object[]> rows = kycDecisionFactRepository.countByDecision(request.getFrom(), request.getTo());
    return ReportTable.builder()
        .headers(List.of("decision", "count"))
        .rows(rows.stream().map(row -> List.of(String.valueOf(row[0]), String.valueOf(row[1]))).toList())
        .build();
  }
}

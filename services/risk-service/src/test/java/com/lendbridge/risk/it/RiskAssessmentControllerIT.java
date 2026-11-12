package com.lendbridge.risk.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.risk.model.RiskAssessment;
import com.lendbridge.risk.model.RiskAssessmentStatus;
import com.lendbridge.risk.model.RiskGrade;
import com.lendbridge.risk.model.TrustTier;
import com.lendbridge.risk.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class RiskAssessmentControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority RISK_OFFICER_AUTHORITY = new SimpleGrantedAuthority("ROLE_RISK_OFFICER");

  @Test
  void getGrade_anyAuthenticatedCaller_returnsGradeOnly() throws Exception {
    RiskAssessment assessment = gradedAssessment();
    riskAssessmentRepository.save(assessment);

    mockMvc
        .perform(
            get("/api/v1/risk-assessments/" + assessment.getLoanId()).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.grade", org.hamcrest.Matchers.equalTo("B")))
        .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("GRADED")));
  }

  @Test
  void getGrade_unknownLoan_returnsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/risk-assessments/" + UUID.randomUUID())
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void getDetail_withoutRiskOfficerRole_isForbidden() throws Exception {
    RiskAssessment assessment = gradedAssessment();
    riskAssessmentRepository.save(assessment);

    mockMvc
        .perform(
            get("/api/v1/risk-assessments/" + assessment.getLoanId() + "/detail")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void getDetail_withRiskOfficerRole_returnsFullDetail() throws Exception {
    RiskAssessment assessment = gradedAssessment();
    riskAssessmentRepository.save(assessment);

    mockMvc
        .perform(
            get("/api/v1/risk-assessments/" + assessment.getLoanId() + "/detail")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.trustTierUsed", org.hamcrest.Matchers.equalTo("TIER_2")))
        .andExpect(jsonPath("$.data.amountKobo", org.hamcrest.Matchers.equalTo(1000000)))
        .andExpect(jsonPath("$.data.expectedDefaultBand").exists());
  }

  @Test
  void getDetail_unknownLoan_returnsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/risk-assessments/" + UUID.randomUUID() + "/detail")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY)))
        .andExpect(status().isNotFound());
  }

  private RiskAssessment gradedAssessment() {
    Instant now = Instant.now();
    return RiskAssessment.builder()
        .loanId(UUID.randomUUID())
        .borrowerUserId(UUID.randomUUID())
        .status(RiskAssessmentStatus.GRADED)
        .grade(RiskGrade.B)
        .modelVersion("rules-v1")
        .trustTierUsed(TrustTier.TIER_2)
        .amountKobo(1_000_000L)
        .tenorMonths(12)
        .interestRateBps(1800)
        .gradedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}

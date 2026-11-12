package com.lendbridge.reporting.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.reporting.model.LoanPipelineFact;
import com.lendbridge.reporting.model.LoanPipelineStatus;
import com.lendbridge.reporting.model.UserSignupFact;
import com.lendbridge.reporting.support.AbstractIntegrationTest;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ReportingControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPER_ADMIN_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPER_ADMIN");

  @Test
  void userGrowth_withoutSuperAdminRole_isForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/reporting/user-growth").with(jwt())).andExpect(status().isForbidden());
  }

  @Test
  void userGrowth_withSuperAdminRole_returnsJsonByDefault() throws Exception {
    userSignupFactRepository.save(
        UserSignupFact.builder()
            .id(UuidV7.generate())
            .userId(UUID.randomUUID())
            .occurredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build());

    mockMvc
        .perform(get("/api/v1/reporting/user-growth").with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.headers[0]").value("day"))
        .andExpect(jsonPath("$.data.headers[1]").value("signups"));
  }

  @Test
  void loanPipeline_csvFormat_streamsTextCsv() throws Exception {
    UUID loanId = UUID.randomUUID();
    loanPipelineFactRepository.save(
        LoanPipelineFact.builder()
            .id(UuidV7.generate())
            .loanId(loanId)
            .borrowerUserId(UUID.randomUUID())
            .status(LoanPipelineStatus.LISTED)
            .amountKobo(1_000_000L)
            .tenorMonths(12)
            .interestRateBps(1500)
            .occurredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build());

    mockMvc
        .perform(
            get("/api/v1/reporting/loan-pipeline")
                .param("format", "csv")
                .with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("status,count,totalAmountKobo")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("LISTED,1,1000000")));
  }

  @Test
  void loanPipeline_pdfFormat_streamsApplicationPdf() throws Exception {
    loanPipelineFactRepository.save(
        LoanPipelineFact.builder()
            .id(UuidV7.generate())
            .loanId(UUID.randomUUID())
            .borrowerUserId(UUID.randomUUID())
            .status(LoanPipelineStatus.LISTED)
            .amountKobo(1_000_000L)
            .tenorMonths(12)
            .interestRateBps(1500)
            .occurredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build());

    mockMvc
        .perform(
            get("/api/v1/reporting/loan-pipeline")
                .param("format", "pdf")
                .with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/pdf"))
        .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
  }

  @Test
  void loanPipeline_excelFormat_streamsXlsxWorkbook() throws Exception {
    loanPipelineFactRepository.save(
        LoanPipelineFact.builder()
            .id(UuidV7.generate())
            .loanId(UUID.randomUUID())
            .borrowerUserId(UUID.randomUUID())
            .status(LoanPipelineStatus.LISTED)
            .amountKobo(1_000_000L)
            .tenorMonths(12)
            .interestRateBps(1500)
            .occurredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build());

    mockMvc
        .perform(
            get("/api/v1/reporting/loan-pipeline")
                .param("format", "excel")
                .with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .contentTypeCompatibleWith(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
  }

  @Test
  void loanPipeline_filtersByDateRange() throws Exception {
    loanPipelineFactRepository.save(
        LoanPipelineFact.builder()
            .id(UuidV7.generate())
            .loanId(UUID.randomUUID())
            .borrowerUserId(UUID.randomUUID())
            .status(LoanPipelineStatus.SUBMITTED)
            .amountKobo(100L)
            .tenorMonths(1)
            .interestRateBps(100)
            .occurredAt(Instant.parse("2020-01-01T00:00:00Z"))
            .build());
    loanPipelineFactRepository.save(
        LoanPipelineFact.builder()
            .id(UuidV7.generate())
            .loanId(UUID.randomUUID())
            .borrowerUserId(UUID.randomUUID())
            .status(LoanPipelineStatus.SUBMITTED)
            .amountKobo(200L)
            .tenorMonths(1)
            .interestRateBps(100)
            .occurredAt(Instant.parse("2026-06-01T00:00:00Z"))
            .build());

    mockMvc
        .perform(
            get("/api/v1/reporting/loan-pipeline")
                .param("from", "2025-01-01T00:00:00Z")
                .with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rows[0][1]").value("1"))
        .andExpect(jsonPath("$.data.rows[0][2]").value("200"));
  }
}

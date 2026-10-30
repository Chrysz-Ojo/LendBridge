package com.lendbridge.loan.it;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.loan.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class LoanReviewControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority RISK_OFFICER_AUTHORITY = new SimpleGrantedAuthority("ROLE_RISK_OFFICER");

  @Test
  void getQueue_rejectsACallerWithoutTheRiskOfficerRole() throws Exception {
    mockMvc
        .perform(get("/api/v1/loans/review/queue").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void decide_rejectsACallerWithoutTheRiskOfficerRole() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/loans/review/" + UUID.randomUUID() + "/decision")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void decide_reject_requiresAReason() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID userId = UUID.randomUUID();

    String created =
        mockMvc
            .perform(
                post("/api/v1/loans")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"amountKobo\":500000,\"tenorMonths\":6,\"purpose\":\"MEDICAL\",\"interestRateBps\":1500}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String loanId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(post("/api/v1/loans/" + loanId + "/submit").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/loans/review/" + loanId + "/decision")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/loans/review/" + loanId + "/decision")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"reason\":\"insufficient income evidence\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("REJECTED")))
        .andExpect(jsonPath("$.data.rejectionReason", equalTo("insufficient income evidence")));

    mockMvc
        .perform(
            post("/api/v1/loans/review/" + loanId + "/decision")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void getQueue_onlyListsRequestsUnderReview() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID userId = UUID.randomUUID();

    String created =
        mockMvc
            .perform(
                post("/api/v1/loans")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"amountKobo\":700000,\"tenorMonths\":8,\"purpose\":\"OTHER\",\"interestRateBps\":1700}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String loanId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(
            get("/api/v1/loans/review/queue")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '" + loanId + "')]").doesNotExist());

    mockMvc
        .perform(post("/api/v1/loans/" + loanId + "/submit").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/loans/review/queue")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '" + loanId + "')]").exists());
  }
}

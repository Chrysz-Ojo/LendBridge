package com.lendbridge.loan.it;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.loan.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class LoanControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority RISK_OFFICER_AUTHORITY = new SimpleGrantedAuthority("ROLE_RISK_OFFICER");

  private String createLoanBody() {
    return "{\"amountKobo\":1200000,\"tenorMonths\":12,\"purpose\":\"BUSINESS\",\"purposeDetail\":\"Inventory\",\"interestRateBps\":1800}";
  }

  @Test
  void create_startsInDraftAndIncludesAScheduleTotal() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/loans")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoanBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status", equalTo("DRAFT")))
        .andExpect(jsonPath("$.data.schedule.instalments.length()", equalTo(12)));
  }

  @Test
  void create_rejectsAnAmountAboveTheBorrowersTrustTierLimit() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/loans")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"amountKobo\":999999999,\"tenorMonths\":12,\"purpose\":\"BUSINESS\",\"interestRateBps\":1800}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void create_rejectsABorrowerWhoHasNotCompletedKyc() throws Exception {
    KYC_STUB.respondNotVerified();
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/loans")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoanBody()))
        .andExpect(status().isUnprocessableEntity());

    KYC_STUB.setTrustTier("TIER_1");
  }

  @Test
  void create_rejectsASecondOpenRequestForTheSameBorrower() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/loans")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoanBody()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/loans")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoanBody()))
        .andExpect(status().isConflict());
  }

  @Test
  void fullFlow_submitApproveScheduleAndGenerateAgreement() throws Exception {
    KYC_STUB.setTrustTier("TIER_2");
    UUID userId = UUID.randomUUID();
    int uploadsBefore = DOCUMENT_STUB.uploadCount();

    String created =
        mockMvc
            .perform(
                post("/api/v1/loans")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createLoanBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String loanId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(post("/api/v1/loans/" + loanId + "/submit").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("UNDER_REVIEW")));

    mockMvc
        .perform(
            post("/api/v1/loans/review/" + loanId + "/decision")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", java.util.List.of("RISK_OFFICER")))
                        .authorities(RISK_OFFICER_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("LISTED")));

    mockMvc
        .perform(get("/api/v1/loans/" + loanId + "/schedule").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.instalments.length()", equalTo(12)));

    mockMvc
        .perform(
            post("/api/v1/loans/" + loanId + "/agreement").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.documentId").exists());

    org.assertj.core.api.Assertions.assertThat(DOCUMENT_STUB.uploadCount()).isEqualTo(uploadsBefore + 1);

    // Idempotent on a second call: no second upload, same document id returned.
    String firstAgreement =
        mockMvc
            .perform(get("/api/v1/loans/" + loanId).with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String documentId = com.jayway.jsonpath.JsonPath.read(firstAgreement, "$.data.agreementDocumentId");

    mockMvc
        .perform(
            post("/api/v1/loans/" + loanId + "/agreement").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.documentId", equalTo(documentId)));

    org.assertj.core.api.Assertions.assertThat(DOCUMENT_STUB.uploadCount()).isEqualTo(uploadsBefore + 1);
  }

  @Test
  void agreement_cannotBeGeneratedBeforeTheLoanIsListed() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID userId = UUID.randomUUID();

    String created =
        mockMvc
            .perform(
                post("/api/v1/loans")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createLoanBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String loanId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(post("/api/v1/loans/" + loanId + "/agreement").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isConflict());
  }

  @Test
  void cancel_fromDraftSucceedsAndFreesTheOpenRequestSlot() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID userId = UUID.randomUUID();

    String created =
        mockMvc
            .perform(
                post("/api/v1/loans")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createLoanBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String loanId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(post("/api/v1/loans/" + loanId + "/cancel").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("CANCELLED")));

    mockMvc
        .perform(
            post("/api/v1/loans")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoanBody()))
        .andExpect(status().isCreated());
  }

  @Test
  void aBorrowerCannotSeeAnotherBorrowersLoan() throws Exception {
    KYC_STUB.setTrustTier("TIER_1");
    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();

    String created =
        mockMvc
            .perform(
                post("/api/v1/loans")
                    .with(jwt().jwt(j -> j.subject(ownerId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createLoanBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String loanId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(get("/api/v1/loans/" + loanId).with(jwt().jwt(j -> j.subject(otherId.toString()))))
        .andExpect(status().isNotFound());
  }
}

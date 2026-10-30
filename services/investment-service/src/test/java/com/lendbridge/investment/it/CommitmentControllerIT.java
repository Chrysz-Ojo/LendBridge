package com.lendbridge.investment.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.investment.model.LoanListing;
import com.lendbridge.investment.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CommitmentControllerIT extends AbstractIntegrationTest {

  private UUID seedActiveLoanListing(long amountKobo) {
    UUID loanId = UUID.randomUUID();
    loanListingRepository.save(
        LoanListing.builder().id(loanId).borrowerUserId(UUID.randomUUID()).amountKobo(amountKobo).active(true).build());
    return loanId;
  }

  @Test
  void create_withinLimits_returnsCreatedAndCallsWalletServiceForAHold() throws Exception {
    UUID loanId = seedActiveLoanListing(1_000_000L);
    int holdsBefore = WALLET_STUB.holdsCreated();

    mockMvc
        .perform(
            post("/api/v1/investment/loans/" + loanId + "/commitments")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .header("Idempotency-Key", "commit-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountKobo\":600000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("ACTIVE")));

    assertThat(WALLET_STUB.holdsCreated()).isEqualTo(holdsBefore + 1);
    assertThat(WALLET_STUB.authorizationHeadersSeen()).isNotEmpty();
  }

  @Test
  void create_missingIdempotencyKey_isRejected() throws Exception {
    UUID loanId = seedActiveLoanListing(1_000_000L);

    mockMvc
        .perform(
            post("/api/v1/investment/loans/" + loanId + "/commitments")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountKobo\":600000}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_belowMinimumTicket_isRejected() throws Exception {
    UUID loanId = seedActiveLoanListing(1_000_000L);

    mockMvc
        .perform(
            post("/api/v1/investment/loans/" + loanId + "/commitments")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .header("Idempotency-Key", "commit-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountKobo\":1000}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_noListingForLoan_returnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/investment/loans/" + UUID.randomUUID() + "/commitments")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .header("Idempotency-Key", "commit-key-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountKobo\":600000}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void createThenCancel_releasesTheHold() throws Exception {
    UUID loanId = seedActiveLoanListing(1_000_000L);
    int releasedBefore = WALLET_STUB.holdsReleased();

    String created =
        mockMvc
            .perform(
                post("/api/v1/investment/loans/" + loanId + "/commitments")
                    .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                    .header("Idempotency-Key", "commit-key-4")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amountKobo\":600000}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String commitmentId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(
            delete("/api/v1/investment/commitments/" + commitmentId)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("CANCELLED")));

    assertThat(WALLET_STUB.holdsReleased()).isEqualTo(releasedBefore + 1);
  }

  @Test
  void create_withoutAuthentication_isRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/investment/loans/" + UUID.randomUUID() + "/commitments")
                .header("Idempotency-Key", "commit-key-5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountKobo\":600000}"))
        .andExpect(status().isUnauthorized());
  }
}

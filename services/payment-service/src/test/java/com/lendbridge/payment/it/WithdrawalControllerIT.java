package com.lendbridge.payment.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.payment.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WithdrawalControllerIT extends AbstractIntegrationTest {

  @AfterEach
  void resetStubs() {
    WALLET_STUB.setAvailableKobo(1_000_000_00L);
    WALLET_STUB.setBankVerificationStatus("VERIFIED");
    AUTH_STUB.setNextVerificationResult(true);
  }

  @Test
  void withdrawalRoundTrip_createsAProcessingPayout() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID bankAccountId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/payments/withdrawals")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "withdrawal-key-1")
                .content(
                    "{\"amountKobo\": 10000, \"bankAccountId\": \""
                        + bankAccountId
                        + "\", \"totpCode\": \"123456\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status", equalTo("PROCESSING")));
  }

  @Test
  void createWithdrawal_rejectedWhenStepUpCodeIsInvalid() throws Exception {
    AUTH_STUB.setNextVerificationResult(false);
    UUID userId = UUID.randomUUID();
    UUID bankAccountId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/payments/withdrawals")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "withdrawal-key-2")
                .content(
                    "{\"amountKobo\": 10000, \"bankAccountId\": \""
                        + bankAccountId
                        + "\", \"totpCode\": \"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", equalTo("TOTP_CODE_INVALID")));
  }

  @Test
  void createWithdrawal_rejectedWhenBankAccountNotVerified() throws Exception {
    WALLET_STUB.setBankVerificationStatus("PENDING");
    UUID userId = UUID.randomUUID();
    UUID bankAccountId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/payments/withdrawals")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "withdrawal-key-3")
                .content(
                    "{\"amountKobo\": 10000, \"bankAccountId\": \""
                        + bankAccountId
                        + "\", \"totpCode\": \"123456\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorCode", equalTo("BANK_ACCOUNT_NOT_VERIFIED")));
  }

  @Test
  void createWithdrawal_idempotentReplay_doesNotCreateASecondPayout() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID bankAccountId = UUID.randomUUID();
    String body =
        "{\"amountKobo\": 10000, \"bankAccountId\": \"" + bankAccountId + "\", \"totpCode\": \"123456\"}";

    String first =
        mockMvc
            .perform(
                post("/api/v1/payments/withdrawals")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "withdrawal-key-4")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstId = com.jayway.jsonpath.JsonPath.read(first, "$.data.id");

    String second =
        mockMvc
            .perform(
                post("/api/v1/payments/withdrawals")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "withdrawal-key-4")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondId = com.jayway.jsonpath.JsonPath.read(second, "$.data.id");

    assertThat(secondId).isEqualTo(firstId);
  }
}

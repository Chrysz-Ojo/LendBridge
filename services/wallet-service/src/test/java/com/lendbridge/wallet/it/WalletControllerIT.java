package com.lendbridge.wallet.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.wallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WalletControllerIT extends AbstractIntegrationTest {

  @Test
  void getWallet_lazilyProvisionsLedgerAccount_thenCreateAndReleaseHold() throws Exception {
    UUID userId = UUID.randomUUID();

    String walletResponse =
        mockMvc
            .perform(get("/api/v1/wallets/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currency", equalTo("NGN")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String walletId = com.jayway.jsonpath.JsonPath.read(walletResponse, "$.data.id");

    assertThat(
            walletRepository.findById(UUID.fromString(walletId)).orElseThrow().getLedgerAccountId())
        .isNotNull();

    String holdResponse =
        mockMvc
            .perform(
                post("/api/v1/wallets/me/holds")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "hold-key-1")
                    .content("{\"amountKobo\": 50000, \"reference\": \"loan-1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status", equalTo("ACTIVE")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String holdId = com.jayway.jsonpath.JsonPath.read(holdResponse, "$.data.id");

    mockMvc
        .perform(get("/api/v1/wallets/me/holds").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()", equalTo(1)));

    mockMvc
        .perform(
            delete("/api/v1/wallets/me/holds/" + holdId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/wallets/me/holds").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()", equalTo(0)));

    // Releasing an already-released hold is a no-op, not an error.
    mockMvc
        .perform(
            delete("/api/v1/wallets/me/holds/" + holdId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());
  }

  @Test
  void createHold_rejectsAmountExceedingAvailableBalance() throws Exception {
    UUID userId = UUID.randomUUID();

    String walletResponse =
        mockMvc
            .perform(get("/api/v1/wallets/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String walletId = com.jayway.jsonpath.JsonPath.read(walletResponse, "$.data.id");
    UUID ledgerAccountId =
        walletRepository.findById(UUID.fromString(walletId)).orElseThrow().getLedgerAccountId();
    LEDGER_STUB.setBalance(ledgerAccountId, 1_000L);

    mockMvc
        .perform(
            post("/api/v1/wallets/me/holds")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "hold-key-2")
                .content("{\"amountKobo\": 5000, \"reference\": \"loan-2\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorCode", equalTo("INSUFFICIENT_FUNDS")));
  }

  @Test
  void createHold_idempotentReplay_doesNotCreateASecondHold() throws Exception {
    UUID userId = UUID.randomUUID();
    mockMvc
        .perform(get("/api/v1/wallets/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());

    String body = "{\"amountKobo\": 1000, \"reference\": \"loan-3\"}";
    String first =
        mockMvc
            .perform(
                post("/api/v1/wallets/me/holds")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "hold-key-3")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstId = com.jayway.jsonpath.JsonPath.read(first, "$.data.id");

    String second =
        mockMvc
            .perform(
                post("/api/v1/wallets/me/holds")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "hold-key-3")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondId = com.jayway.jsonpath.JsonPath.read(second, "$.data.id");

    assertThat(secondId).isEqualTo(firstId);
    mockMvc
        .perform(get("/api/v1/wallets/me/holds").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(jsonPath("$.data.length()", equalTo(1)));
  }

  @Test
  void addBankAccount_thenListsIt() throws Exception {
    UUID userId = UUID.randomUUID();
    mockMvc
        .perform(get("/api/v1/wallets/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/wallets/me/bank-accounts")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"bankCode\": \"058\", \"bankName\": \"GTBank\", \"accountNumber\": \"0123456789\", \"accountHolderName\": \"Jane Doe\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.verificationStatus", equalTo("VERIFIED")))
        .andExpect(jsonPath("$.data.maskedAccountNumber", equalTo("******6789")));

    mockMvc
        .perform(
            get("/api/v1/wallets/me/bank-accounts")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()", equalTo(1)));
  }
}

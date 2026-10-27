package com.lendbridge.ledger.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.ledger.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class LedgerControllerIT extends AbstractIntegrationTest {

  @Test
  void openAccounts_postJournalEntry_thenBalancesAndEntryRoundTrip() throws Exception {
    String walletAccountId = openAccount("WALLET", "DEBIT");
    String feeAccountId = openAccount("FEE", "CREDIT");

    String journalEntryResponse =
        mockMvc
            .perform(
                post("/api/v1/ledger/journal-entries")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "test-key-1")
                    .content(
                        """
                        {
                          "currency": "NGN",
                          "reference": "test-topup",
                          "postings": [
                            {"accountId": "%s", "direction": "DEBIT", "amountKobo": 500000},
                            {"accountId": "%s", "direction": "CREDIT", "amountKobo": 500000}
                          ]
                        }
                        """
                            .formatted(walletAccountId, feeAccountId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.postings.length()", equalTo(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String journalEntryId = com.jayway.jsonpath.JsonPath.read(journalEntryResponse, "$.data.id");

    mockMvc
        .perform(get("/api/v1/ledger/journal-entries/" + journalEntryId).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reference", equalTo("test-topup")));

    mockMvc
        .perform(get("/api/v1/ledger/accounts/" + walletAccountId + "/balance").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.balanceKobo", equalTo(500000)));

    mockMvc
        .perform(get("/api/v1/ledger/accounts/" + feeAccountId + "/balance").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.balanceKobo", equalTo(500000)));

    mockMvc
        .perform(get("/api/v1/ledger/accounts/" + walletAccountId + "/postings").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.postings.length()", equalTo(1)));

    assertThat(journalEntryRepository.findAllById(java.util.List.of(java.util.UUID.fromString(journalEntryId))))
        .hasSize(1);
  }

  @Test
  void postJournalEntry_idempotentReplay_doesNotDoubleCount() throws Exception {
    String walletAccountId = openAccount("WALLET", "DEBIT");
    String feeAccountId = openAccount("FEE", "CREDIT");
    String body =
        """
        {
          "currency": "NGN",
          "postings": [
            {"accountId": "%s", "direction": "DEBIT", "amountKobo": 100000},
            {"accountId": "%s", "direction": "CREDIT", "amountKobo": 100000}
          ]
        }
        """
            .formatted(walletAccountId, feeAccountId);

    String firstResponse =
        mockMvc
            .perform(
                post("/api/v1/ledger/journal-entries")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "replay-key")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstId = com.jayway.jsonpath.JsonPath.read(firstResponse, "$.data.id");

    String secondResponse =
        mockMvc
            .perform(
                post("/api/v1/ledger/journal-entries")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "replay-key")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondId = com.jayway.jsonpath.JsonPath.read(secondResponse, "$.data.id");

    assertThat(secondId).isEqualTo(firstId);
    mockMvc
        .perform(get("/api/v1/ledger/accounts/" + walletAccountId + "/balance").with(jwt()))
        .andExpect(jsonPath("$.data.balanceKobo", equalTo(100000)));
  }

  @Test
  void postJournalEntry_rejectsUnbalancedEntry() throws Exception {
    String walletAccountId = openAccount("WALLET", "DEBIT");
    String feeAccountId = openAccount("FEE", "CREDIT");

    mockMvc
        .perform(
            post("/api/v1/ledger/journal-entries")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "unbalanced-key")
                .content(
                    """
                    {
                      "currency": "NGN",
                      "postings": [
                        {"accountId": "%s", "direction": "DEBIT", "amountKobo": 1000},
                        {"accountId": "%s", "direction": "CREDIT", "amountKobo": 500}
                      ]
                    }
                    """
                        .formatted(walletAccountId, feeAccountId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorCode", equalTo("JOURNAL_ENTRY_UNBALANCED")));
  }

  @Test
  void postJournalEntry_requiresIdempotencyKey() throws Exception {
    String walletAccountId = openAccount("WALLET", "DEBIT");
    String feeAccountId = openAccount("FEE", "CREDIT");

    mockMvc
        .perform(
            post("/api/v1/ledger/journal-entries")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "NGN",
                      "postings": [
                        {"accountId": "%s", "direction": "DEBIT", "amountKobo": 1000},
                        {"accountId": "%s", "direction": "CREDIT", "amountKobo": 1000}
                      ]
                    }
                    """
                        .formatted(walletAccountId, feeAccountId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", equalTo("IDEMPOTENCY_KEY_REQUIRED")));
  }

  private String openAccount(String accountType, String normalBalance) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/ledger/accounts")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountType": "%s", "currency": "NGN", "normalBalance": "%s"}
                        """
                            .formatted(accountType, normalBalance)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
  }
}

package com.lendbridge.payment.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.payment.provider.impl.fake.FakePaymentProvider;
import com.lendbridge.payment.provider.ProviderWebhookEventType;
import com.lendbridge.payment.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TopUpControllerIT extends AbstractIntegrationTest {

  @Test
  void topUpRoundTrip_createThenWebhookDrivesStatusToSucceeded() throws Exception {
    UUID userId = UUID.randomUUID();

    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/payments/top-ups")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "topup-key-1")
                    .content("{\"amountKobo\": 50000, \"currency\": \"NGN\", \"customerEmail\": \"a@b.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status", equalTo("AWAITING_PROVIDER")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String paymentIntentId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.id");
    String providerReference =
        paymentIntentRepository.findById(UUID.fromString(paymentIntentId)).orElseThrow().getProviderReference();

    String webhookBody =
        FakePaymentProvider.buildWebhookBody(ProviderWebhookEventType.CHARGE_SUCCEEDED, providerReference, 50000);
    mockMvc
        .perform(
            post("/api/v1/webhooks/fake")
                .headers(headersFromMap(FakePaymentProvider.validSignatureHeader()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/payments/top-ups/" + paymentIntentId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("SUCCEEDED")));
  }

  @Test
  void createTopUp_idempotentReplay_doesNotCreateASecondPaymentIntent() throws Exception {
    UUID userId = UUID.randomUUID();
    String body = "{\"amountKobo\": 20000, \"currency\": \"NGN\", \"customerEmail\": \"c@d.com\"}";

    String first =
        mockMvc
            .perform(
                post("/api/v1/payments/top-ups")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "topup-key-2")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstId = com.jayway.jsonpath.JsonPath.read(first, "$.data.id");

    String second =
        mockMvc
            .perform(
                post("/api/v1/payments/top-ups")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "topup-key-2")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondId = com.jayway.jsonpath.JsonPath.read(second, "$.data.id");

    assertThat(secondId).isEqualTo(firstId);
  }

  @Test
  void createTopUp_missingIdempotencyKey_isRejected() throws Exception {
    UUID userId = UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/v1/payments/top-ups")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountKobo\": 10000, \"currency\": \"NGN\", \"customerEmail\": \"a@b.com\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", equalTo("IDEMPOTENCY_KEY_REQUIRED")));
  }

  @Test
  void webhook_rejectsAnInvalidSignature() throws Exception {
    String webhookBody =
        FakePaymentProvider.buildWebhookBody(ProviderWebhookEventType.CHARGE_SUCCEEDED, "some-ref", 1000);
    mockMvc
        .perform(
            post("/api/v1/webhooks/fake")
                .header("x-fake-signature", "not-the-valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody))
        .andExpect(status().isBadRequest());
  }

  private org.springframework.http.HttpHeaders headersFromMap(java.util.Map<String, String> map) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    map.forEach(headers::add);
    return headers;
  }
}

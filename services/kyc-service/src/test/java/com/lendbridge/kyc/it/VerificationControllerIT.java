package com.lendbridge.kyc.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.kyc.provider.impl.fake.FakeVerificationProvider;
import com.lendbridge.kyc.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class VerificationControllerIT extends AbstractIntegrationTest {

  @Test
  void startVerification_allChecksPass_autoVerifiesAtTierOne() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID idCardId = DOCUMENT_STUB.registerDocument("ID_CARD", "CLEAN", "id.jpg");
    UUID selfieId = DOCUMENT_STUB.registerDocument("SELFIE", "CLEAN", "selfie.jpg");

    mockMvc
        .perform(
            post("/api/v1/kyc/verifications")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(startVerificationBody("12345678901", null, idCardId, selfieId, null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("VERIFIED")))
        .andExpect(jsonPath("$.data.trustTier", org.hamcrest.Matchers.equalTo("TIER_1")));

    mockMvc
        .perform(get("/api/v1/kyc/verifications/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("VERIFIED")));
  }

  @Test
  void startVerification_mismatchedBvn_staysPendingAndIsIdempotentOnReplay() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID idCardId = DOCUMENT_STUB.registerDocument("ID_CARD", "CLEAN", "id.jpg");
    UUID selfieId = DOCUMENT_STUB.registerDocument("SELFIE", "CLEAN", "selfie.jpg");
    String mismatchBvn = FakeVerificationProvider.mismatchSentinel("12345678901");

    String first =
        mockMvc
            .perform(
                post("/api/v1/kyc/verifications")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(startVerificationBody(mismatchBvn, null, idCardId, selfieId, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("PENDING")))
            .andExpect(jsonPath("$.data.reviewRequired", org.hamcrest.Matchers.equalTo(true)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstId = com.jayway.jsonpath.JsonPath.read(first, "$.data.id");

    String second =
        mockMvc
            .perform(
                post("/api/v1/kyc/verifications")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(startVerificationBody(mismatchBvn, null, idCardId, selfieId, null)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondId = com.jayway.jsonpath.JsonPath.read(second, "$.data.id");

    org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
  }

  @Test
  void startVerification_failedBvn_autoRejects() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID idCardId = DOCUMENT_STUB.registerDocument("ID_CARD", "CLEAN", "id.jpg");
    UUID selfieId = DOCUMENT_STUB.registerDocument("SELFIE", "CLEAN", "selfie.jpg");
    String failedBvn = FakeVerificationProvider.failSentinel("12345678901");

    mockMvc
        .perform(
            post("/api/v1/kyc/verifications")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(startVerificationBody(failedBvn, null, idCardId, selfieId, null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("REJECTED")));
  }

  @Test
  void startVerification_withWrongDocumentType_isUnprocessable() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID notAnIdCard = DOCUMENT_STUB.registerDocument("SELFIE", "CLEAN", "selfie.jpg");
    UUID selfieId = DOCUMENT_STUB.registerDocument("SELFIE", "CLEAN", "selfie.jpg");

    mockMvc
        .perform(
            post("/api/v1/kyc/verifications")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(startVerificationBody("12345678901", null, notAnIdCard, selfieId, null)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void getMyStatus_withNoCase_returnsNotFound() throws Exception {
    UUID userId = UUID.randomUUID();
    mockMvc
        .perform(get("/api/v1/kyc/verifications/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isNotFound());
  }

  private String startVerificationBody(String bvn, String nin, UUID idCardId, UUID selfieId, UUID bankStatementId) {
    return "{"
        + "\"bvn\":\""
        + bvn
        + "\","
        + "\"nin\":"
        + (nin == null ? "null" : "\"" + nin + "\"")
        + ","
        + "\"fullName\":\"Jane Doe\","
        + "\"dateOfBirth\":\"1990-01-01\","
        + "\"idCardDocumentId\":\""
        + idCardId
        + "\","
        + "\"selfieDocumentId\":\""
        + selfieId
        + "\","
        + "\"bankStatementDocumentId\":"
        + (bankStatementId == null ? "null" : "\"" + bankStatementId + "\"")
        + "}";
  }
}

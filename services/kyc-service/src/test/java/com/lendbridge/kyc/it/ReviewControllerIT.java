package com.lendbridge.kyc.it;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.kyc.provider.impl.fake.FakeVerificationProvider;
import com.lendbridge.kyc.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReviewControllerIT extends AbstractIntegrationTest {

  @Test
  void getQueue_rejectsACallerWithoutTheKycOfficerRole() throws Exception {
    mockMvc
        .perform(get("/api/v1/kyc/review/queue").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void decide_rejectsACallerWithoutTheKycOfficerRole() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/kyc/review/" + UUID.randomUUID() + "/decision")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void queueAndDecide_officerApprovesAMismatchedCase() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID idCardId = DOCUMENT_STUB.registerDocument("ID_CARD", "CLEAN", "id.jpg");
    UUID selfieId = DOCUMENT_STUB.registerDocument("SELFIE", "CLEAN", "selfie.jpg");
    String mismatchBvn = FakeVerificationProvider.mismatchSentinel("12345678901");

    String created =
        mockMvc
            .perform(
                post("/api/v1/kyc/verifications")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"bvn\":\""
                            + mismatchBvn
                            + "\",\"nin\":null,\"fullName\":\"Jane Doe\",\"dateOfBirth\":\"1990-01-01\","
                            + "\"idCardDocumentId\":\""
                            + idCardId
                            + "\",\"selfieDocumentId\":\""
                            + selfieId
                            + "\",\"bankStatementDocumentId\":null}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String caseId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(
            get("/api/v1/kyc/review/queue")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("KYC_OFFICER")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '" + caseId + "')]").exists());

    mockMvc
        .perform(
            post("/api/v1/kyc/review/" + caseId + "/decision")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("KYC_OFFICER"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("VERIFIED")))
        .andExpect(jsonPath("$.data.trustTier", equalTo("TIER_1")));
  }

  @Test
  void decide_reject_requiresAReasonAndPublishesKycRejected() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID idCardId = DOCUMENT_STUB.registerDocument("ID_CARD", "CLEAN", "id.jpg");
    UUID selfieId = DOCUMENT_STUB.registerDocument("SELFIE", "CLEAN", "selfie.jpg");
    String mismatchBvn = FakeVerificationProvider.mismatchSentinel("12345678901");

    String created =
        mockMvc
            .perform(
                post("/api/v1/kyc/verifications")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"bvn\":\""
                            + mismatchBvn
                            + "\",\"nin\":null,\"fullName\":\"Jane Doe\",\"dateOfBirth\":\"1990-01-01\","
                            + "\"idCardDocumentId\":\""
                            + idCardId
                            + "\",\"selfieDocumentId\":\""
                            + selfieId
                            + "\",\"bankStatementDocumentId\":null}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String caseId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(
            post("/api/v1/kyc/review/" + caseId + "/decision")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("KYC_OFFICER"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/kyc/review/" + caseId + "/decision")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("KYC_OFFICER"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"reason\":\"fraudulent documents\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("REJECTED")))
        .andExpect(jsonPath("$.data.rejectionReason", equalTo("fraudulent documents")));
  }
}

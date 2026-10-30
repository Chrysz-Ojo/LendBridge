package com.lendbridge.kyc.provider.impl.smileidentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.provider.BvnCheckRequest;
import com.lendbridge.kyc.provider.LivenessCheckRequest;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** No live network call — only the fixed-payload mapping logic is exercised, same convention as
 * payment-service's real PSP adapter tests (see the approved plan's Tests section). */
class SmileIdentityVerificationProviderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void checkBvn_mapsAVerifiedMatchToPassed() throws Exception {
    SmileIdentityClient client = mock(SmileIdentityClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"result\":{\"ResultCode\":\"1012\",\"ResultText\":\"Verified\",\"FullNameMatch\":true,\"DOBMatch\":true}}");
    when(client.submitIdVerification(any())).thenReturn(response);

    SmileIdentityVerificationProvider provider = new SmileIdentityVerificationProvider(client);
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(provider.name()).isEqualTo(ProviderName.SMILE_IDENTITY);
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
    assertThat(result.getNameMatch()).isTrue();
  }

  @Test
  void checkBvn_mapsANonMatchingButExistingRecordToMismatch() throws Exception {
    SmileIdentityClient client = mock(SmileIdentityClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"result\":{\"ResultCode\":\"1013\",\"ResultText\":\"Not Verified\",\"FullNameMatch\":false,\"DOBMatch\":false}}");
    when(client.submitIdVerification(any())).thenReturn(response);

    SmileIdentityVerificationProvider provider = new SmileIdentityVerificationProvider(client);
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
  }

  @Test
  void checkNin_mapsANonExistentRecordToFailed() throws Exception {
    SmileIdentityClient client = mock(SmileIdentityClient.class);
    JsonNode response = objectMapper.readTree("{\"result\":{\"ResultCode\":\"0000\",\"ResultText\":\"Not Found\"}}");
    when(client.submitIdVerification(any())).thenReturn(response);

    SmileIdentityVerificationProvider provider = new SmileIdentityVerificationProvider(client);
    ProviderCheckResult result = provider.checkNin(new com.lendbridge.kyc.provider.NinCheckRequest(
        "98765432109", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
  }

  @Test
  void checkLiveness_mapsHighConfidenceLiveMatchToPassed() throws Exception {
    SmileIdentityClient client = mock(SmileIdentityClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"result\":{\"ConfidenceValue\":0.97,\"Actions\":{\"Liveness_Check\":\"Live\"},\"Confidence\":0.96}}");
    when(client.submitSmartSelfieVerification(any())).thenReturn(response);

    SmileIdentityVerificationProvider provider = new SmileIdentityVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
    assertThat(result.getLivenessPassed()).isTrue();
    assertThat(result.getFaceMatchPassed()).isTrue();
  }

  @Test
  void checkLiveness_mapsLowConfidenceMatchToMismatch() throws Exception {
    SmileIdentityClient client = mock(SmileIdentityClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"result\":{\"ConfidenceValue\":0.9,\"Actions\":{\"Liveness_Check\":\"Live\"},\"Confidence\":0.4}}");
    when(client.submitSmartSelfieVerification(any())).thenReturn(response);

    SmileIdentityVerificationProvider provider = new SmileIdentityVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
    assertThat(result.getFaceMatchPassed()).isFalse();
  }
}

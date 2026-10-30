package com.lendbridge.kyc.provider.impl.prembly;

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
import com.lendbridge.kyc.provider.NinCheckRequest;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** No live network call — only the fixed-payload mapping logic is exercised (see the approved
 * plan's Tests section). */
class PremblyVerificationProviderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void checkBvn_mapsAFullMatchToPassed() throws Exception {
    PremblyClient client = mock(PremblyClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"status\":true,\"verification\":{\"data\":{\"full_name_comparison\":\"match\",\"date_of_birth_comparison\":\"match\"}}}");
    when(client.verifyBvn(any())).thenReturn(response);

    PremblyVerificationProvider provider = new PremblyVerificationProvider(client);
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(provider.name()).isEqualTo(ProviderName.PREMBLY);
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
  }

  @Test
  void checkBvn_mapsAPartialMatchToMismatch() throws Exception {
    PremblyClient client = mock(PremblyClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"status\":true,\"verification\":{\"data\":{\"full_name_comparison\":\"no_match\",\"date_of_birth_comparison\":\"match\"}}}");
    when(client.verifyBvn(any())).thenReturn(response);

    PremblyVerificationProvider provider = new PremblyVerificationProvider(client);
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
  }

  @Test
  void checkNin_mapsAFailedStatusToFailed() throws Exception {
    PremblyClient client = mock(PremblyClient.class);
    JsonNode response = objectMapper.readTree("{\"status\":false}");
    when(client.verifyNin(any())).thenReturn(response);

    PremblyVerificationProvider provider = new PremblyVerificationProvider(client);
    ProviderCheckResult result = provider.checkNin(new NinCheckRequest("98765432109", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
  }

  @Test
  void checkLiveness_mapsAHighConfidenceLiveMatchToPassed() throws Exception {
    PremblyClient client = mock(PremblyClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"status\":true,\"verification\":{\"data\":{\"confidence\":0.9,\"liveness\":{\"is_alive\":true,\"probability\":0.85}}}}");
    when(client.verifyFaceMatch(any())).thenReturn(response);

    PremblyVerificationProvider provider = new PremblyVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
    assertThat(result.getLivenessPassed()).isTrue();
    assertThat(result.getFaceMatchPassed()).isTrue();
  }

  @Test
  void checkLiveness_mapsALowConfidenceMatchToMismatch() throws Exception {
    PremblyClient client = mock(PremblyClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"status\":true,\"verification\":{\"data\":{\"confidence\":0.5,\"liveness\":{\"is_alive\":true,\"probability\":0.85}}}}");
    when(client.verifyFaceMatch(any())).thenReturn(response);

    PremblyVerificationProvider provider = new PremblyVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
    assertThat(result.getFaceMatchPassed()).isFalse();
  }

  @Test
  void checkLiveness_mapsAFailedStatusToFailed() throws Exception {
    PremblyClient client = mock(PremblyClient.class);
    JsonNode response = objectMapper.readTree("{\"status\":false}");
    when(client.verifyFaceMatch(any())).thenReturn(response);

    PremblyVerificationProvider provider = new PremblyVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
  }
}

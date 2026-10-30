package com.lendbridge.kyc.provider.impl.dojah;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
class DojahVerificationProviderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void checkBvn_mapsAMatchingNameToPassed() throws Exception {
    DojahClient client = mock(DojahClient.class);
    JsonNode response = objectMapper.readTree("{\"entity\":{\"first_name\":\"Jane\",\"last_name\":\"Doe\"}}");
    when(client.lookupBvn(anyString())).thenReturn(response);

    DojahVerificationProvider provider = new DojahVerificationProvider(client);
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(provider.name()).isEqualTo(ProviderName.DOJAH);
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
  }

  @Test
  void checkBvn_mapsANonMatchingNameToMismatch() throws Exception {
    DojahClient client = mock(DojahClient.class);
    JsonNode response = objectMapper.readTree("{\"entity\":{\"first_name\":\"John\",\"last_name\":\"Smith\"}}");
    when(client.lookupBvn(anyString())).thenReturn(response);

    DojahVerificationProvider provider = new DojahVerificationProvider(client);
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
  }

  @Test
  void checkNin_mapsAMissingEntityToFailed() throws Exception {
    DojahClient client = mock(DojahClient.class);
    JsonNode response = objectMapper.readTree("{}");
    when(client.lookupNin(anyString())).thenReturn(response);

    DojahVerificationProvider provider = new DojahVerificationProvider(client);
    ProviderCheckResult result = provider.checkNin(new NinCheckRequest("98765432109", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
  }

  @Test
  void checkLiveness_mapsALiveMatchToPassed() throws Exception {
    DojahClient client = mock(DojahClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"entity\":{\"selfie_verification\":{\"match\":true,\"confidence_value\":0.95},"
                + "\"liveness_check\":{\"live\":true,\"probability\":0.9}}}");
    when(client.verifyPhotoId(any())).thenReturn(response);

    DojahVerificationProvider provider = new DojahVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
    assertThat(result.getLivenessPassed()).isTrue();
    assertThat(result.getFaceMatchPassed()).isTrue();
  }

  @Test
  void checkLiveness_mapsANonMatchingSelfieToMismatch() throws Exception {
    DojahClient client = mock(DojahClient.class);
    JsonNode response =
        objectMapper.readTree(
            "{\"entity\":{\"selfie_verification\":{\"match\":false,\"confidence_value\":0.3},"
                + "\"liveness_check\":{\"live\":true,\"probability\":0.9}}}");
    when(client.verifyPhotoId(any())).thenReturn(response);

    DojahVerificationProvider provider = new DojahVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
    assertThat(result.getFaceMatchPassed()).isFalse();
  }

  @Test
  void checkLiveness_mapsAMissingEntityToFailed() throws Exception {
    DojahClient client = mock(DojahClient.class);
    JsonNode response = objectMapper.readTree("{}");
    when(client.verifyPhotoId(any())).thenReturn(response);

    DojahVerificationProvider provider = new DojahVerificationProvider(client);
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));

    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
  }
}

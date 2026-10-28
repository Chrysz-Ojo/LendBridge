package com.lendbridge.kyc.provider.impl.prembly;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.provider.BvnCheckRequest;
import com.lendbridge.kyc.provider.LivenessCheckRequest;
import com.lendbridge.kyc.provider.NinCheckRequest;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import com.lendbridge.kyc.provider.VerificationProvider;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Real adapter against Prembly's Identitypass BVN/NIN verification and face-verification
 * endpoints, built from general knowledge of Prembly's public API shape rather than confirmed
 * against live/current docs this session (see this service's final report). Never invoked by
 * anything automated in this repo; exercised only by its own unit tests against fixed sample
 * payloads, never a live network call.
 */
@Component
public class PremblyVerificationProvider implements VerificationProvider {

  private final PremblyClient client;

  public PremblyVerificationProvider(PremblyClient client) {
    this.client = client;
  }

  @Override
  public ProviderName name() {
    return ProviderName.PREMBLY;
  }

  @Override
  public ProviderCheckResult checkBvn(BvnCheckRequest request) {
    JsonNode response =
        client.verifyBvn(
            Map.of("number", request.getBvn(), "full_name", request.getExpectedFullName() == null ? "" : request.getExpectedFullName()));
    return toIdentityResult(response);
  }

  @Override
  public ProviderCheckResult checkNin(NinCheckRequest request) {
    JsonNode response =
        client.verifyNin(
            Map.of("number", request.getNin(), "full_name", request.getExpectedFullName() == null ? "" : request.getExpectedFullName()));
    return toIdentityResult(response);
  }

  private ProviderCheckResult toIdentityResult(JsonNode response) {
    boolean status = response.path("status").asBoolean(false);
    if (!status) {
      return ProviderCheckResult.builder().outcome(CheckOutcome.FAILED).rawResponseJson(response.toString()).build();
    }
    JsonNode verification = response.path("verification").path("data");
    boolean nameMatch = "match".equalsIgnoreCase(verification.path("full_name_comparison").asText(""));
    boolean dobMatch = "match".equalsIgnoreCase(verification.path("date_of_birth_comparison").asText(""));
    return ProviderCheckResult.builder()
        .outcome(nameMatch && dobMatch ? CheckOutcome.PASSED : CheckOutcome.MISMATCH)
        .nameMatch(nameMatch)
        .dobMatch(dobMatch)
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderCheckResult checkLiveness(LivenessCheckRequest request) {
    JsonNode response =
        client.verifyFaceMatch(
            Map.of(
                "selfie_document_id", request.getSelfieDocumentId().toString(),
                "id_document_id", request.getIdCardDocumentId().toString()));
    boolean status = response.path("status").asBoolean(false);
    JsonNode data = response.path("verification").path("data");
    double faceMatchScore = data.path("confidence").asDouble(0);
    boolean faceMatchPassed = status && faceMatchScore >= 0.8;
    boolean livenessPassed = data.path("liveness").path("is_alive").asBoolean(false);
    double livenessScore = data.path("liveness").path("probability").asDouble(0);
    CheckOutcome outcome =
        !status ? CheckOutcome.FAILED : (faceMatchPassed && livenessPassed ? CheckOutcome.PASSED : CheckOutcome.MISMATCH);
    return ProviderCheckResult.builder()
        .outcome(outcome)
        .livenessScore(livenessScore)
        .livenessPassed(livenessPassed)
        .faceMatchScore(faceMatchScore)
        .faceMatchPassed(faceMatchPassed)
        .rawResponseJson(response.toString())
        .build();
  }
}

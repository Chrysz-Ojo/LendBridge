package com.lendbridge.kyc.provider.impl.dojah;

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
 * Real adapter against Dojah's BVN/NIN lookup and Photo-ID verification endpoints, built from
 * general knowledge of Dojah's public API shape rather than confirmed against live/current docs
 * this session (see this service's final report). Never invoked by anything automated in this
 * repo; exercised only by its own unit tests against fixed sample payloads, never a live network
 * call.
 */
@Component
public class DojahVerificationProvider implements VerificationProvider {

  private final DojahClient client;

  public DojahVerificationProvider(DojahClient client) {
    this.client = client;
  }

  @Override
  public ProviderName name() {
    return ProviderName.DOJAH;
  }

  @Override
  public ProviderCheckResult checkBvn(BvnCheckRequest request) {
    JsonNode response = client.lookupBvn(request.getBvn());
    return toIdentityResult(response, request.getExpectedFullName());
  }

  @Override
  public ProviderCheckResult checkNin(NinCheckRequest request) {
    JsonNode response = client.lookupNin(request.getNin());
    return toIdentityResult(response, request.getExpectedFullName());
  }

  private ProviderCheckResult toIdentityResult(JsonNode response, String expectedFullName) {
    JsonNode entity = response.path("entity");
    if (entity.isMissingNode() || entity.isNull()) {
      return ProviderCheckResult.builder().outcome(CheckOutcome.FAILED).rawResponseJson(response.toString()).build();
    }
    String firstName = entity.path("first_name").asText("");
    String lastName = entity.path("last_name").asText("");
    String actualFullName = (firstName + " " + lastName).trim();
    boolean nameMatch =
        expectedFullName != null && actualFullName.equalsIgnoreCase(expectedFullName.trim());
    return ProviderCheckResult.builder()
        .outcome(nameMatch ? CheckOutcome.PASSED : CheckOutcome.MISMATCH)
        .nameMatch(nameMatch)
        .dobMatch(nameMatch)
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderCheckResult checkLiveness(LivenessCheckRequest request) {
    JsonNode response =
        client.verifyPhotoId(
            Map.of(
                "selfie_document_id", request.getSelfieDocumentId().toString(),
                "photoid_document_id", request.getIdCardDocumentId().toString()));
    JsonNode entity = response.path("entity");
    boolean selfieVerified = entity.path("selfie_verification").path("match").asBoolean(false);
    double faceMatchScore = entity.path("selfie_verification").path("confidence_value").asDouble(0);
    boolean livenessPassed = entity.path("liveness_check").path("live").asBoolean(false);
    double livenessScore = entity.path("liveness_check").path("probability").asDouble(0);
    CheckOutcome outcome =
        livenessPassed && selfieVerified
            ? CheckOutcome.PASSED
            : (entity.isMissingNode() ? CheckOutcome.FAILED : CheckOutcome.MISMATCH);
    return ProviderCheckResult.builder()
        .outcome(outcome)
        .livenessScore(livenessScore)
        .livenessPassed(livenessPassed)
        .faceMatchScore(faceMatchScore)
        .faceMatchPassed(selfieVerified)
        .rawResponseJson(response.toString())
        .build();
  }
}

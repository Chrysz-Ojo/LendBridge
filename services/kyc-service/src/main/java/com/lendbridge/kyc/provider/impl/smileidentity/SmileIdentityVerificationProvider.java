package com.lendbridge.kyc.provider.impl.smileidentity;

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
 * Real adapter against Smile Identity's Enhanced KYC / SmartSelfie Verification products, built
 * from general knowledge of Smile ID's public API shape rather than confirmed against live/current
 * docs this session (see this service's final report). Never invoked by anything automated in this
 * repo; exercised only by its own unit tests against fixed sample payloads, never a live network
 * call.
 */
@Component
public class SmileIdentityVerificationProvider implements VerificationProvider {

  private final SmileIdentityClient client;

  public SmileIdentityVerificationProvider(SmileIdentityClient client) {
    this.client = client;
  }

  @Override
  public ProviderName name() {
    return ProviderName.SMILE_IDENTITY;
  }

  @Override
  public ProviderCheckResult checkBvn(BvnCheckRequest request) {
    return checkIdNumber("BVN_NUMBER", request.getBvn(), request.getExpectedFullName());
  }

  @Override
  public ProviderCheckResult checkNin(NinCheckRequest request) {
    return checkIdNumber("NIN_V2", request.getNin(), request.getExpectedFullName());
  }

  private ProviderCheckResult checkIdNumber(String idType, String idNumber, String expectedFullName) {
    JsonNode response =
        client.submitIdVerification(
            Map.of(
                "id_info",
                Map.of("country", "NG", "id_type", idType, "id_number", idNumber, "full_name", expectedFullName)));
    JsonNode result = response.path("result");
    String actualStatus = result.path("ResultText").asText("");
    boolean matched = "Verified".equalsIgnoreCase(actualStatus) || result.path("FullNameMatch").asBoolean(false);
    return ProviderCheckResult.builder()
        .outcome(mapOutcome(result, matched))
        .nameMatch(matched)
        .dobMatch(result.path("DOBMatch").asBoolean(matched))
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderCheckResult checkLiveness(LivenessCheckRequest request) {
    JsonNode response =
        client.submitSmartSelfieVerification(
            Map.of(
                "selfie_document_id", request.getSelfieDocumentId().toString(),
                "id_card_document_id", request.getIdCardDocumentId().toString()));
    JsonNode result = response.path("result");
    double livenessScore = result.path("ConfidenceValue").asDouble(0);
    boolean livenessPassed = "Live".equalsIgnoreCase(result.path("Actions").path("Liveness_Check").asText(""));
    double faceMatchScore = result.path("Confidence").asDouble(0);
    boolean faceMatchPassed = faceMatchScore >= 0.8;
    CheckOutcome outcome =
        livenessPassed && faceMatchPassed
            ? CheckOutcome.PASSED
            : (livenessScore <= 0 && faceMatchScore <= 0 ? CheckOutcome.FAILED : CheckOutcome.MISMATCH);
    return ProviderCheckResult.builder()
        .outcome(outcome)
        .livenessScore(livenessScore)
        .livenessPassed(livenessPassed)
        .faceMatchScore(faceMatchScore)
        .faceMatchPassed(faceMatchPassed)
        .rawResponseJson(response.toString())
        .build();
  }

  private CheckOutcome mapOutcome(JsonNode result, boolean matched) {
    boolean exists = result.path("ResultCode").asText("").startsWith("1");
    if (!exists) {
      return CheckOutcome.FAILED;
    }
    return matched ? CheckOutcome.PASSED : CheckOutcome.MISMATCH;
  }
}

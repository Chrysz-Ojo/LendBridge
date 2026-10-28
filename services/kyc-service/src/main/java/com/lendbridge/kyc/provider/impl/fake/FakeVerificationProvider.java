package com.lendbridge.kyc.provider.impl.fake;

import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.provider.BvnCheckRequest;
import com.lendbridge.kyc.provider.LivenessCheckRequest;
import com.lendbridge.kyc.provider.NinCheckRequest;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import com.lendbridge.kyc.provider.VerificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fake KYC provider for local development and tests, same pattern as payment-service's
 * {@code FakePaymentProvider} (CLAUDE.md's external-provider rule). Deterministic, EICAR-style: a
 * BVN/NIN/filename-hint containing {@link #FAIL_SENTINEL} always fails the relevant check, one
 * containing {@link #MISMATCH_SENTINEL} always mismatches, everything else passes. This is what
 * every test and CI run exercises; the three real adapters are never invoked by anything automated.
 */
@Component
public class FakeVerificationProvider implements VerificationProvider {

  private static final Logger log = LoggerFactory.getLogger(FakeVerificationProvider.class);
  public static final String FAIL_SENTINEL = "SENTINEL_FAIL";
  public static final String MISMATCH_SENTINEL = "SENTINEL_MISMATCH";

  @Override
  public ProviderName name() {
    return ProviderName.FAKE;
  }

  @Override
  public ProviderCheckResult checkBvn(BvnCheckRequest request) {
    CheckOutcome outcome = outcomeFor(request.getBvn());
    log.info("Fake BVN check for {}: {}", mask(request.getBvn()), outcome);
    return identityResult(outcome);
  }

  @Override
  public ProviderCheckResult checkNin(NinCheckRequest request) {
    CheckOutcome outcome = outcomeFor(request.getNin());
    log.info("Fake NIN check for {}: {}", mask(request.getNin()), outcome);
    return identityResult(outcome);
  }

  @Override
  public ProviderCheckResult checkLiveness(LivenessCheckRequest request) {
    String combinedHint = combine(request.getSelfieFilenameHint(), request.getIdCardFilenameHint());
    CheckOutcome outcome = outcomeFor(combinedHint);
    log.info("Fake liveness check: {}", outcome);
    return switch (outcome) {
      case FAILED ->
          ProviderCheckResult.builder()
              .outcome(CheckOutcome.FAILED)
              .livenessScore(0.05)
              .livenessPassed(false)
              .faceMatchScore(0.05)
              .faceMatchPassed(false)
              .rawResponseJson("{\"fake\":true}")
              .build();
      case MISMATCH ->
          ProviderCheckResult.builder()
              .outcome(CheckOutcome.MISMATCH)
              .livenessScore(0.95)
              .livenessPassed(true)
              .faceMatchScore(0.40)
              .faceMatchPassed(false)
              .rawResponseJson("{\"fake\":true}")
              .build();
      case PASSED ->
          ProviderCheckResult.builder()
              .outcome(CheckOutcome.PASSED)
              .livenessScore(0.97)
              .livenessPassed(true)
              .faceMatchScore(0.96)
              .faceMatchPassed(true)
              .rawResponseJson("{\"fake\":true}")
              .build();
    };
  }

  private ProviderCheckResult identityResult(CheckOutcome outcome) {
    boolean matched = outcome == CheckOutcome.PASSED;
    return ProviderCheckResult.builder()
        .outcome(outcome)
        .nameMatch(matched)
        .dobMatch(matched)
        .rawResponseJson("{\"fake\":true}")
        .build();
  }

  private CheckOutcome outcomeFor(String value) {
    if (value != null && value.contains(FAIL_SENTINEL)) {
      return CheckOutcome.FAILED;
    }
    if (value != null && value.contains(MISMATCH_SENTINEL)) {
      return CheckOutcome.MISMATCH;
    }
    return CheckOutcome.PASSED;
  }

  private String combine(String a, String b) {
    return (a == null ? "" : a) + " " + (b == null ? "" : b);
  }

  private String mask(String value) {
    if (value == null || value.length() < 4) {
      return "****";
    }
    return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
  }

  public static String failSentinel(String base) {
    return base + "-" + FAIL_SENTINEL;
  }

  public static String mismatchSentinel(String base) {
    return base + "-" + MISMATCH_SENTINEL;
  }
}

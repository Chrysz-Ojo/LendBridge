package com.lendbridge.kyc.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.TrustTier;
import org.junit.jupiter.api.Test;

class TrustTierCalculatorTest {

  @Test
  void tier1_whenOnlyBvnAndFaceMatchPass() {
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.PASSED)
                .faceMatchPassed(true)
                .livenessPassed(true)
                .build());
    assertThat(tier).isEqualTo(TrustTier.TIER_1);
  }

  @Test
  void tier2_whenNinAlsoPasses() {
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.PASSED)
                .ninOutcome(CheckOutcome.PASSED)
                .faceMatchPassed(true)
                .livenessPassed(true)
                .build());
    assertThat(tier).isEqualTo(TrustTier.TIER_2);
  }

  @Test
  void tier3_whenLivenessAndBankStatementAlsoPass() {
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.PASSED)
                .ninOutcome(CheckOutcome.PASSED)
                .faceMatchPassed(true)
                .livenessPassed(true)
                .bankStatementOutcome(CheckOutcome.PASSED)
                .build());
    assertThat(tier).isEqualTo(TrustTier.TIER_3);
  }

  @Test
  void returnsNull_whenFaceMatchDidNotPassAndMismatchIsNotAllowed() {
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.PASSED)
                .faceMatchPassed(false)
                .livenessPassed(true)
                .build());
    assertThat(tier).isNull();
  }

  @Test
  void allowMismatch_letsAManuallyApprovedBvnMismatchReachTierOne_whenFaceMatchActuallyPassed() {
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.MISMATCH)
                .faceMatchPassed(true)
                .livenessPassed(true)
                .allowMismatch(true)
                .build());
    assertThat(tier).isEqualTo(TrustTier.TIER_1);
  }

  @Test
  void allowMismatch_stillReturnsNull_whenFaceMatchActuallyFailed() {
    // A BVN/NIN mismatch being overridden by an officer must never silently wave through a real
    // face-match failure — this is the asymmetric case FakeVerificationProvider's MISMATCH liveness
    // branch models (a real selfie that genuinely doesn't match the ID photo).
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.MISMATCH)
                .faceMatchPassed(false)
                .livenessPassed(true)
                .allowMismatch(true)
                .build());
    assertThat(tier).isNull();
  }

  @Test
  void allowMismatch_doesNotReachTierThree_whenLivenessActuallyFailed() {
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.MISMATCH)
                .ninOutcome(CheckOutcome.MISMATCH)
                .faceMatchPassed(true)
                .livenessPassed(false)
                .bankStatementOutcome(CheckOutcome.PASSED)
                .allowMismatch(true)
                .build());
    assertThat(tier).isEqualTo(TrustTier.TIER_2);
  }

  @Test
  void doesNotReachTierTwo_whenNinWasNeverAttempted() {
    TrustTier tier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(CheckOutcome.PASSED)
                .faceMatchPassed(true)
                .livenessPassed(true)
                .bankStatementOutcome(CheckOutcome.PASSED)
                .build());
    assertThat(tier).isEqualTo(TrustTier.TIER_1);
  }
}

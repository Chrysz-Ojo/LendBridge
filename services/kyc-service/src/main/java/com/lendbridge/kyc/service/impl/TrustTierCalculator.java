package com.lendbridge.kyc.service.impl;

import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.TrustTier;

final class TrustTierCalculator {

  private TrustTierCalculator() {}

  static TrustTier compute(TrustTierCalculationRequest request) {
    // allowMismatch only relaxes the coarse BVN/NIN registry outcomes (no finer-grained signal exists
    // for those). Face match and liveness have their own booleans straight from the provider and must
    // always be checked for real — an officer resolving an unrelated BVN/NIN mismatch must never
    // silently clear a failed face-match or liveness signal.
    boolean effectiveFaceMatch = Boolean.TRUE.equals(request.getFaceMatchPassed());
    boolean tier1 = satisfied(request.getBvnOutcome(), request.isAllowMismatch()) && effectiveFaceMatch;
    if (!tier1) {
      return null;
    }
    boolean tier2 = request.getNinOutcome() != null && satisfied(request.getNinOutcome(), request.isAllowMismatch());
    boolean effectiveLivenessPassed = Boolean.TRUE.equals(request.getLivenessPassed());
    boolean tier3 = tier2 && effectiveLivenessPassed && request.getBankStatementOutcome() == CheckOutcome.PASSED;
    if (tier3) {
      return TrustTier.TIER_3;
    }
    if (tier2) {
      return TrustTier.TIER_2;
    }
    return TrustTier.TIER_1;
  }

  private static boolean satisfied(CheckOutcome outcome, boolean allowMismatch) {
    return outcome == CheckOutcome.PASSED || (allowMismatch && outcome == CheckOutcome.MISMATCH);
  }
}

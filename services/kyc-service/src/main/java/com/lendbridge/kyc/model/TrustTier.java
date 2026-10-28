package com.lendbridge.kyc.model;

/**
 * Tier 1 = BVN {@code PASSED} and face-match passed. Tier 2 = Tier 1 and NIN {@code PASSED}. Tier
 * 3 = Tier 2 and liveness passed and bank-statement outcome {@code PASSED}. See the approved plan's
 * "Data model and trust-tier rules" section.
 */
public enum TrustTier {
  TIER_1,
  TIER_2,
  TIER_3
}

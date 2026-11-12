package com.lendbridge.risk.model;

/** A local copy of kyc-service's own {@code TrustTier} values (Tier 1 = BVN + face-match, Tier 2 =
 * + NIN, Tier 3 = + liveness + bank statement) — risk-service never depends on kyc-service's
 * module, only the {@code trustTier} string carried on its {@code kyc.verified} event. */
public enum TrustTier {
  TIER_1,
  TIER_2,
  TIER_3
}

package com.lendbridge.kyc.provider;

import com.lendbridge.kyc.model.CheckOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One result shape reused across all three check types, same as payment-service's
 * {@code ProviderChargeResult} is reused across charge/payout verbs — fields that don't apply to a
 * given check type are simply left null. For {@code checkBvn}/{@code checkNin}: {@code outcome},
 * {@code nameMatch}, {@code dobMatch}. For {@code checkLiveness}: {@code outcome},
 * {@code livenessScore}, {@code livenessPassed}, {@code faceMatchScore}, {@code faceMatchPassed}.
 *
 * <p>{@code outcome} is the provider's own aggregate verdict for the check (it already folds
 * together the finer-grained match signals) — a provider must only ever return
 * {@link CheckOutcome#PASSED} when every match signal for that check type is true, since downstream
 * trust-tier computation in auto-verification mode trusts {@code outcome} alone, not the individual
 * booleans (see {@code VerificationServiceImpl}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderCheckResult {
  private CheckOutcome outcome;
  private Boolean nameMatch;
  private Boolean dobMatch;
  private Double livenessScore;
  private Boolean livenessPassed;
  private Double faceMatchScore;
  private Boolean faceMatchPassed;
  private String rawResponseJson;
}

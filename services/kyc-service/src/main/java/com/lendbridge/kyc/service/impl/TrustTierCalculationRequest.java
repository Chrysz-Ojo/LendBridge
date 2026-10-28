package com.lendbridge.kyc.service.impl;

import com.lendbridge.kyc.model.CheckOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Inputs to {@link TrustTierCalculator#compute(TrustTierCalculationRequest)}, package-private since
 * it only exists to keep that call site self-describing — several of these fields are adjacent
 * {@code Boolean}s/{@code CheckOutcome}s that are easy to transpose when passed positionally.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TrustTierCalculationRequest {
  private CheckOutcome bvnOutcome;
  private CheckOutcome ninOutcome;
  private Boolean faceMatchPassed;
  private Boolean livenessPassed;
  private CheckOutcome bankStatementOutcome;
  private boolean allowMismatch;
}

package com.lendbridge.risk.service.impl;

import com.lendbridge.risk.model.TrustTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The two amount thresholds are the only configurable inputs ({@code @Value}-injected into
 * {@link com.lendbridge.risk.service.impl.RiskAssessmentServiceImpl}, same "configurable limits"
 * convention loan-service already uses) — tenor and rate bands are fixed by the rules-v1 scorecard
 * itself. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskGradeCalculationRequest {
  private TrustTier trustTier;
  private long amountKobo;
  private int tenorMonths;
  private int interestRateBps;
  private long amountThreshold1Kobo;
  private long amountThreshold2Kobo;
}

package com.lendbridge.risk.service.impl;

import com.lendbridge.risk.model.RiskGrade;

/**
 * {@code rules-v1}: a deterministic additive point scorecard (same shape as kyc-service's
 * {@code TrustTierCalculator}) — trust tier is the heaviest single weight, but loan terms combined
 * (max 6 points) can outweigh it (max 3 points): an aggressive ask on a thin trust tier lands at E;
 * a small/short/cheap loan on the best tier lands at A. See the approved plan's grading rule.
 *
 * <pre>
 * trustTierPoints: TIER_3=0, TIER_2=1, TIER_1=3
 * amountPoints:    0 / 1 / 2 across the two configurable amountKobo thresholds
 * tenorPoints:     0 (&lt;=12mo) / 1 (&lt;=24mo) / 2 (&gt;24mo)
 * ratePoints:      0 (&lt;=1500bps) / 1 (&lt;=2500bps) / 2 (&gt;2500bps)
 * total = sum (range 0-9) -&gt; grade: 0-1=A, 2-3=B, 4-5=C, 6-7=D, 8-9=E
 * </pre>
 */
public final class RiskGradeCalculator {

  public static final String MODEL_VERSION = "rules-v1";

  private RiskGradeCalculator() {}

  public static RiskGrade compute(RiskGradeCalculationRequest request) {
    int points =
        trustTierPoints(request)
            + amountPoints(request)
            + tenorPoints(request)
            + ratePoints(request);
    if (points <= 1) {
      return RiskGrade.A;
    }
    if (points <= 3) {
      return RiskGrade.B;
    }
    if (points <= 5) {
      return RiskGrade.C;
    }
    if (points <= 7) {
      return RiskGrade.D;
    }
    return RiskGrade.E;
  }

  private static int trustTierPoints(RiskGradeCalculationRequest request) {
    return switch (request.getTrustTier()) {
      case TIER_3 -> 0;
      case TIER_2 -> 1;
      case TIER_1 -> 3;
    };
  }

  private static int amountPoints(RiskGradeCalculationRequest request) {
    if (request.getAmountKobo() <= request.getAmountThreshold1Kobo()) {
      return 0;
    }
    if (request.getAmountKobo() <= request.getAmountThreshold2Kobo()) {
      return 1;
    }
    return 2;
  }

  private static int tenorPoints(RiskGradeCalculationRequest request) {
    if (request.getTenorMonths() <= 12) {
      return 0;
    }
    if (request.getTenorMonths() <= 24) {
      return 1;
    }
    return 2;
  }

  private static int ratePoints(RiskGradeCalculationRequest request) {
    if (request.getInterestRateBps() <= 1500) {
      return 0;
    }
    if (request.getInterestRateBps() <= 2500) {
      return 1;
    }
    return 2;
  }

  /** Non-persisted, grade-derived display label only — never stored or published (see the
   * approved plan's grading rule: real DTI needs borrower income, which doesn't exist anywhere in
   * this service's inputs in this scope). */
  public static String expectedDefaultBandLabel(RiskGrade grade) {
    return switch (grade) {
      case A -> "Very low";
      case B -> "Low";
      case C -> "Moderate";
      case D -> "High";
      case E -> "Very high";
    };
  }
}

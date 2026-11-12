package com.lendbridge.risk.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.risk.model.RiskGrade;
import com.lendbridge.risk.model.TrustTier;
import org.junit.jupiter.api.Test;

class RiskGradeCalculatorTest {

  private static final long THRESHOLD1 = 50_000_000L;
  private static final long THRESHOLD2 = 200_000_000L;

  private static RiskGradeCalculationRequest.RiskGradeCalculationRequestBuilder baseRequest() {
    return RiskGradeCalculationRequest.builder()
        .amountThreshold1Kobo(THRESHOLD1)
        .amountThreshold2Kobo(THRESHOLD2);
  }

  @Test
  void bestTierAndCheapestTerms_gradesA() {
    RiskGrade grade =
        RiskGradeCalculator.compute(
            baseRequest()
                .trustTier(TrustTier.TIER_3)
                .amountKobo(THRESHOLD1)
                .tenorMonths(12)
                .interestRateBps(1500)
                .build());
    assertThat(grade).isEqualTo(RiskGrade.A);
  }

  @Test
  void thinTierAndAggressiveTerms_gradesE() {
    RiskGrade grade =
        RiskGradeCalculator.compute(
            baseRequest()
                .trustTier(TrustTier.TIER_1)
                .amountKobo(THRESHOLD2 + 1)
                .tenorMonths(25)
                .interestRateBps(2501)
                .build());
    assertThat(grade).isEqualTo(RiskGrade.E);
  }

  @Test
  void loanTermsAloneCanOutweighTheBestTrustTier() {
    // TIER_3 contributes 0 points (the best possible trust-tier score) but aggressive loan terms
    // (max 6 points) still drag the grade down to D — loan terms outweigh trust tier alone (max 3
    // points), per the approved plan's grading rule.
    RiskGrade grade =
        RiskGradeCalculator.compute(
            baseRequest()
                .trustTier(TrustTier.TIER_3)
                .amountKobo(THRESHOLD2 + 1)
                .tenorMonths(25)
                .interestRateBps(2501)
                .build());
    assertThat(grade).isEqualTo(RiskGrade.D);
  }

  @Test
  void amountPoints_zeroAtOrBelowFirstThreshold() {
    RiskGrade grade =
        RiskGradeCalculator.compute(
            baseRequest().trustTier(TrustTier.TIER_3).amountKobo(THRESHOLD1).tenorMonths(12).interestRateBps(1500).build());
    assertThat(grade).isEqualTo(RiskGrade.A);
  }

  @Test
  void amountPoints_oneJustAboveFirstThreshold() {
    RiskGrade above =
        RiskGradeCalculator.compute(
            baseRequest()
                .trustTier(TrustTier.TIER_3)
                .amountKobo(THRESHOLD1 + 1)
                .tenorMonths(12)
                .interestRateBps(1500)
                .build());
    // trustTier=0 + amount=1 + tenor=0 + rate=0 = 1 -> still A (boundary of the A/B cutoff)
    assertThat(above).isEqualTo(RiskGrade.A);
  }

  @Test
  void amountPoints_twoAboveSecondThreshold() {
    RiskGrade grade =
        RiskGradeCalculator.compute(
            baseRequest()
                .trustTier(TrustTier.TIER_2)
                .amountKobo(THRESHOLD2 + 1)
                .tenorMonths(12)
                .interestRateBps(1500)
                .build());
    // trustTier=1 + amount=2 + tenor=0 + rate=0 = 3 -> B
    assertThat(grade).isEqualTo(RiskGrade.B);
  }

  @Test
  void tenorPoints_boundaryAt12And13Months() {
    RiskGrade at12 =
        RiskGradeCalculator.compute(
            baseRequest().trustTier(TrustTier.TIER_2).amountKobo(0).tenorMonths(12).interestRateBps(0).build());
    RiskGrade at13 =
        RiskGradeCalculator.compute(
            baseRequest().trustTier(TrustTier.TIER_2).amountKobo(0).tenorMonths(13).interestRateBps(0).build());
    // trustTier=1: at12 total=1 -> A; at13 total=2 -> B
    assertThat(at12).isEqualTo(RiskGrade.A);
    assertThat(at13).isEqualTo(RiskGrade.B);
  }

  @Test
  void ratePoints_boundaryAt1500And1501Bps() {
    RiskGrade at1500 =
        RiskGradeCalculator.compute(
            baseRequest().trustTier(TrustTier.TIER_3).amountKobo(0).tenorMonths(1).interestRateBps(1500).build());
    RiskGrade at1501 =
        RiskGradeCalculator.compute(
            baseRequest().trustTier(TrustTier.TIER_3).amountKobo(0).tenorMonths(1).interestRateBps(1501).build());
    assertThat(at1500).isEqualTo(RiskGrade.A);
    assertThat(at1501).isEqualTo(RiskGrade.A); // trustTier=0 + rate=1 = 1 -> still A
  }

  @Test
  void expectedDefaultBandLabel_coversEveryGrade() {
    assertThat(RiskGradeCalculator.expectedDefaultBandLabel(RiskGrade.A)).isEqualTo("Very low");
    assertThat(RiskGradeCalculator.expectedDefaultBandLabel(RiskGrade.B)).isEqualTo("Low");
    assertThat(RiskGradeCalculator.expectedDefaultBandLabel(RiskGrade.C)).isEqualTo("Moderate");
    assertThat(RiskGradeCalculator.expectedDefaultBandLabel(RiskGrade.D)).isEqualTo("High");
    assertThat(RiskGradeCalculator.expectedDefaultBandLabel(RiskGrade.E)).isEqualTo("Very high");
  }
}

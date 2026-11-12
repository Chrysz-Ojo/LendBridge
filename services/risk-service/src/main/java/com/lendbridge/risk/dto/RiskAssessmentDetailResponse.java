package com.lendbridge.risk.dto;

import com.lendbridge.risk.model.RiskAssessmentStatus;
import com.lendbridge.risk.model.RiskGrade;
import com.lendbridge.risk.model.TrustTier;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full audit-sensitive detail — trust tier used, loan-term snapshot, rejection reason if any.
 * {@code RISK_OFFICER}-gated, same audit-sensitive-detail precedent as loan-service's
 * {@code LoanReviewController}. {@code expectedDefaultBand} is a non-persisted, grade-derived
 * display label only (see {@link com.lendbridge.risk.service.impl.RiskGradeCalculator}) — real DTI
 * needs borrower income, which doesn't exist anywhere in this service's inputs in this scope.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentDetailResponse {
  private UUID loanId;
  private UUID borrowerUserId;
  private RiskAssessmentStatus status;
  private RiskGrade grade;
  private String modelVersion;
  private String expectedDefaultBand;
  private TrustTier trustTierUsed;
  private long amountKobo;
  private int tenorMonths;
  private int interestRateBps;
  private String kycRejectionReason;
}

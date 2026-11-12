package com.lendbridge.risk.dto;

import com.lendbridge.risk.model.RiskAssessmentStatus;
import com.lendbridge.risk.model.RiskGrade;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lender-visible display data only — grade/status, nothing audit-sensitive. Returned by
 * {@code GET /api/v1/risk-assessments/{loanId}} for any authenticated caller. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskGradeResponse {
  private UUID loanId;
  private RiskAssessmentStatus status;
  private RiskGrade grade;
  private String modelVersion;
}

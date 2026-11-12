package com.lendbridge.adminbff.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentDetailResponse {
  private UUID loanId;
  private UUID borrowerUserId;
  private String status;
  private String grade;
  private String modelVersion;
  private String expectedDefaultBand;
  private String trustTierUsed;
  private long amountKobo;
  private int tenorMonths;
  private int interestRateBps;
  private String kycRejectionReason;
}

package com.lendbridge.risk.service;

import com.lendbridge.risk.dto.KycRejectedEvent;
import com.lendbridge.risk.dto.KycVerifiedEvent;
import com.lendbridge.risk.dto.LoanSubmittedEvent;
import com.lendbridge.risk.dto.RiskAssessmentDetailResponse;
import com.lendbridge.risk.dto.RiskGradeResponse;
import java.util.UUID;

public interface RiskAssessmentService {

  RiskGradeResponse getGrade(UUID loanId);

  RiskAssessmentDetailResponse getDetail(UUID loanId);

  void handleLoanSubmitted(LoanSubmittedEvent event);

  void handleKycVerified(KycVerifiedEvent event);

  void handleKycRejected(KycRejectedEvent event);
}

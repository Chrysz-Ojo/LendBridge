package com.lendbridge.risk.controller;

import com.lendbridge.risk.dto.RiskAssessmentDetailResponse;
import com.lendbridge.risk.dto.RiskGradeResponse;
import com.lendbridge.risk.service.RiskAssessmentService;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code @PreAuthorize("hasRole('RISK_OFFICER')")} on {@code /detail} reuses the same precedent
 * as loan-service's {@code LoanReviewController} and kyc-service's {@code ReviewController}. */
@RestController
@RequestMapping("/api/v1/risk-assessments")
@RequiredArgsConstructor
public class RiskAssessmentController {

  private final RiskAssessmentService riskAssessmentService;

  @GetMapping("/{loanId}")
  public ResponseEntity<ApiResponse<RiskGradeResponse>> getGrade(@PathVariable UUID loanId) {
    return ResponseEntity.ok(ApiResponse.success(riskAssessmentService.getGrade(loanId)));
  }

  @GetMapping("/{loanId}/detail")
  @PreAuthorize("hasRole('RISK_OFFICER')")
  public ResponseEntity<ApiResponse<RiskAssessmentDetailResponse>> getDetail(@PathVariable UUID loanId) {
    return ResponseEntity.ok(ApiResponse.success(riskAssessmentService.getDetail(loanId)));
  }
}

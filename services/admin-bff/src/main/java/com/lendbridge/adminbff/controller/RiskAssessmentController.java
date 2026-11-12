package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.RiskAssessmentDetailResponse;
import com.lendbridge.adminbff.risk.RiskAssessmentGateway;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/risk/assessments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RISK_OFFICER')")
public class RiskAssessmentController {

  private final RiskAssessmentGateway riskAssessmentGateway;

  @GetMapping("/{loanId}/detail")
  public ResponseEntity<ApiResponse<RiskAssessmentDetailResponse>> getDetail(@PathVariable UUID loanId) {
    return ResponseEntity.ok(ApiResponse.success(riskAssessmentGateway.getDetail(loanId)));
  }
}

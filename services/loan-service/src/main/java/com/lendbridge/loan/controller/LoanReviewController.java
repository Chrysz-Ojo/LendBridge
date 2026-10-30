package com.lendbridge.loan.controller;

import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.LoanReviewDecisionRequest;
import com.lendbridge.loan.dto.LoanReviewQueueItemResponse;
import com.lendbridge.loan.security.CurrentUser;
import com.lendbridge.loan.service.LoanReviewService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code @PreAuthorize("hasRole('RISK_OFFICER')")} reuses the precedent established by
 * kyc-service's {@code ReviewController} ({@code hasRole('KYC_OFFICER')}) and payment-service's
 * {@code ReconciliationController} ({@code hasRole('FINANCE')}). */
@RestController
@RequestMapping("/api/v1/loans/review")
@RequiredArgsConstructor
public class LoanReviewController {

  private final LoanReviewService loanReviewService;

  @GetMapping("/queue")
  @PreAuthorize("hasRole('RISK_OFFICER')")
  public ResponseEntity<ApiResponse<List<LoanReviewQueueItemResponse>>> getQueue() {
    return ResponseEntity.ok(ApiResponse.success(loanReviewService.getQueue()));
  }

  @PostMapping("/{id}/decision")
  @PreAuthorize("hasRole('RISK_OFFICER')")
  public ResponseEntity<ApiResponse<LoanResponse>> decide(
      Authentication authentication, @PathVariable UUID id, @Valid @RequestBody LoanReviewDecisionRequest request) {
    LoanResponse response = loanReviewService.decide(id, request, CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

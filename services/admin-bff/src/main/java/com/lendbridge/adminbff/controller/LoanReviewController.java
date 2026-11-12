package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.LoanReviewQueueItemResponse;
import com.lendbridge.adminbff.loan.LoanReviewGateway;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only passthrough to loan-service's review queue. Deciding a flagged loan (approve/reject,
 * including a grade override) no longer has a direct endpoint here — it goes through
 * {@code ApprovalController} with {@code actionType=LOAN_REVIEW_DECISION}, since a human overriding
 * a computed risk grade is sensitive enough to warrant a second {@code RISK_OFFICER}'s sign-off
 * (see ADR 0023). */
@RestController
@RequestMapping("/api/v1/admin/loans/review")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RISK_OFFICER')")
public class LoanReviewController {

  private final LoanReviewGateway loanReviewGateway;

  @GetMapping("/queue")
  public ResponseEntity<ApiResponse<List<LoanReviewQueueItemResponse>>> getQueue() {
    return ResponseEntity.ok(ApiResponse.success(loanReviewGateway.getQueue()));
  }
}

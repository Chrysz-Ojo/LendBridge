package com.lendbridge.kyc.controller;

import com.lendbridge.kyc.dto.ReviewDecisionRequest;
import com.lendbridge.kyc.dto.ReviewQueueItemResponse;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.security.CurrentUser;
import com.lendbridge.kyc.service.ReviewService;
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

/** {@code @PreAuthorize("hasRole('KYC_OFFICER')")} reuses the precedent already established by
 * payment-service's {@code ReconciliationController} (which uses {@code hasRole('FINANCE')}). */
@RestController
@RequestMapping("/api/v1/kyc/review")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  @GetMapping("/queue")
  @PreAuthorize("hasRole('KYC_OFFICER')")
  public ResponseEntity<ApiResponse<List<ReviewQueueItemResponse>>> getQueue() {
    return ResponseEntity.ok(ApiResponse.success(reviewService.getQueue()));
  }

  @PostMapping("/{caseId}/decision")
  @PreAuthorize("hasRole('KYC_OFFICER')")
  public ResponseEntity<ApiResponse<VerificationStatusResponse>> decide(
      Authentication authentication, @PathVariable UUID caseId, @Valid @RequestBody ReviewDecisionRequest request) {
    VerificationStatusResponse response =
        reviewService.decide(caseId, request, CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.KycReviewDecisionRequest;
import com.lendbridge.adminbff.dto.KycReviewQueueItemResponse;
import com.lendbridge.adminbff.dto.KycVerificationStatusResponse;
import com.lendbridge.adminbff.kyc.KycReviewGateway;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin proxy to kyc-service's own {@code ReviewController} — same role requirement
 * ({@code KYC_OFFICER}) mirrored here as defense in depth (kyc-service independently re-enforces
 * it too, via the forwarded JWT — see {@code FeignAuthForwardingConfig}). */
@RestController
@RequestMapping("/api/v1/admin/kyc/review")
@RequiredArgsConstructor
@PreAuthorize("hasRole('KYC_OFFICER')")
public class KycReviewController {

  private final KycReviewGateway kycReviewGateway;

  @GetMapping("/queue")
  public ResponseEntity<ApiResponse<List<KycReviewQueueItemResponse>>> getQueue() {
    return ResponseEntity.ok(ApiResponse.success(kycReviewGateway.getQueue()));
  }

  @PostMapping("/{caseId}/decision")
  public ResponseEntity<ApiResponse<KycVerificationStatusResponse>> decide(
      @PathVariable UUID caseId, @Valid @RequestBody KycReviewDecisionRequest request) {
    return ResponseEntity.ok(ApiResponse.success(kycReviewGateway.decide(caseId, request)));
  }
}

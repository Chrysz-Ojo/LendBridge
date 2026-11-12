package com.lendbridge.adminbff.kyc;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.KycReviewDecisionRequest;
import com.lendbridge.adminbff.dto.KycReviewQueueItemResponse;
import com.lendbridge.adminbff.dto.KycVerificationStatusResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "kyc-service",
    url = "${KYC_SERVICE_URI:http://localhost:8093}",
    configuration = FeignAuthForwardingConfig.class)
public interface KycReviewClient {

  @GetMapping("/api/v1/kyc/review/queue")
  ApiResponse<List<KycReviewQueueItemResponse>> getQueue();

  @PostMapping("/api/v1/kyc/review/{caseId}/decision")
  ApiResponse<KycVerificationStatusResponse> decide(
      @PathVariable("caseId") UUID caseId, @RequestBody KycReviewDecisionRequest request);
}

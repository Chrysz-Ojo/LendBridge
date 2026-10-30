package com.lendbridge.loan.kyc;

import com.lendbridge.starter.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
    name = "kyc-service",
    url = "${KYC_SERVICE_URL:http://localhost:8093}",
    configuration = KycFeignConfig.class)
public interface KycClient {

  @GetMapping("/api/v1/kyc/verifications/me")
  ApiResponse<KycStatusResponse> getMyStatus();
}

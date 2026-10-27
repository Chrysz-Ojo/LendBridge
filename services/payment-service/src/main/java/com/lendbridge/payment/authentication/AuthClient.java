package com.lendbridge.payment.authentication;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.dto.MessageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "authentication-service",
    url = "${AUTH_SERVICE_URL:http://localhost:8081}",
    configuration = AuthFeignConfig.class)
public interface AuthClient {

  @PostMapping("/api/v1/auth/mfa/totp/step-up-verify")
  ApiResponse<MessageResponse> stepUpVerify(@RequestBody TotpStepUpCommand request);
}

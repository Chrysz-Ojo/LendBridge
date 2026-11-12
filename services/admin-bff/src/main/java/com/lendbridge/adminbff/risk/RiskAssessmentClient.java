package com.lendbridge.adminbff.risk;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.RiskAssessmentDetailResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "risk-service",
    url = "${RISK_SERVICE_URI:http://localhost:8098}",
    configuration = FeignAuthForwardingConfig.class)
public interface RiskAssessmentClient {

  @GetMapping("/api/v1/risk-assessments/{loanId}/detail")
  ApiResponse<RiskAssessmentDetailResponse> getDetail(@PathVariable("loanId") UUID loanId);
}

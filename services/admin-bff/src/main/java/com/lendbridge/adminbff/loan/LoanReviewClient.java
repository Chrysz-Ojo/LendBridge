package com.lendbridge.adminbff.loan;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.LoanResponse;
import com.lendbridge.adminbff.dto.LoanReviewDecisionRequest;
import com.lendbridge.adminbff.dto.LoanReviewQueueItemResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "loan-service",
    url = "${LOAN_SERVICE_URI:http://localhost:8094}",
    configuration = FeignAuthForwardingConfig.class)
public interface LoanReviewClient {

  @GetMapping("/api/v1/loans/review/queue")
  ApiResponse<List<LoanReviewQueueItemResponse>> getQueue();

  @PostMapping("/api/v1/loans/review/{id}/decision")
  ApiResponse<LoanResponse> decide(@PathVariable("id") UUID id, @RequestBody LoanReviewDecisionRequest request);
}

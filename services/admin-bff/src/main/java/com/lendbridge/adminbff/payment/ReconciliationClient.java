package com.lendbridge.adminbff.payment;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.ReconciliationRunRequest;
import com.lendbridge.adminbff.dto.ReconciliationRunResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "payment-service",
    url = "${PAYMENT_SERVICE_URI:http://localhost:8092}",
    configuration = FeignAuthForwardingConfig.class)
public interface ReconciliationClient {

  @PostMapping("/api/v1/payments/reconciliation-runs")
  ApiResponse<ReconciliationRunResponse> runReconciliation(@RequestBody ReconciliationRunRequest request);

  @GetMapping("/api/v1/payments/reconciliation-runs/{id}")
  ApiResponse<ReconciliationRunResponse> getReconciliationRun(@PathVariable("id") UUID id);
}

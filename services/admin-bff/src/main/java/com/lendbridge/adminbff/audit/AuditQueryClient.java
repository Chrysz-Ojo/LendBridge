package com.lendbridge.adminbff.audit;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.AuditRecordResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "audit-service",
    url = "${AUDIT_SERVICE_URI:http://localhost:8101}",
    configuration = FeignAuthForwardingConfig.class)
public interface AuditQueryClient {

  @GetMapping("/api/v1/audit/records")
  ApiResponse<List<AuditRecordResponse>> query(
      @RequestParam(value = "topic", required = false) String topic,
      @RequestParam(value = "aggregateKey", required = false) String aggregateKey,
      @RequestParam(value = "from", required = false) Instant from,
      @RequestParam(value = "to", required = false) Instant to,
      @RequestParam(value = "limit", required = false) Integer limit);
}

package com.lendbridge.adminbff.messaging;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import com.lendbridge.adminbff.dto.AbuseReportResponse;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "messaging-service",
    url = "${MESSAGING_SERVICE_URI:http://localhost:8099}",
    configuration = FeignAuthForwardingConfig.class)
public interface AbuseReportClient {

  @GetMapping("/api/v1/messaging/admin/reports")
  ApiResponse<List<AbuseReportResponse>> findOpen();

  @PatchMapping("/api/v1/messaging/admin/reports/{reportId}/resolve")
  ApiResponse<AbuseReportResponse> resolve(@PathVariable("reportId") UUID reportId);
}

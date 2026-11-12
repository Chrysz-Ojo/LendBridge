package com.lendbridge.adminbff.reporting;

import com.lendbridge.adminbff.config.FeignAuthForwardingConfig;
import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Raw passthrough, not {@code ApiResponse<T>}-unwrapped like every other integration — see
 * {@link ReportingGateway} and ADR 0023: reporting-service's own endpoints already apply the
 * ApiResponse envelope for {@code format=json} and stream raw {@code text/csv}/{@code
 * application/pdf}/{@code .xlsx} bytes for the download formats, so admin-bff forwards the
 * response verbatim (status, headers, body) rather than unwrapping and re-wrapping it. */
@FeignClient(
    name = "reporting-service",
    url = "${REPORTING_SERVICE_URI:http://localhost:8102}",
    configuration = FeignAuthForwardingConfig.class)
public interface ReportingClient {

  @GetMapping("/api/v1/reporting/{reportName}")
  ResponseEntity<byte[]> get(
      @PathVariable("reportName") String reportName,
      @RequestParam(value = "from", required = false) Instant from,
      @RequestParam(value = "to", required = false) Instant to,
      @RequestParam(value = "format", required = false) String format);
}

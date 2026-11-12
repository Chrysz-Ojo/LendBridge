package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.reporting.ReportingGateway;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Raw passthrough for all 5 reports and all formats — see {@code ReportingClient}'s javadoc for
 * why this is the one controller in the service that doesn't re-wrap in {@code ApiResponse}. */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ReportingController {

  private final ReportingGateway reportingGateway;

  @GetMapping("/{reportName}")
  public ResponseEntity<byte[]> get(
      @PathVariable String reportName,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return reportingGateway.get(reportName, from, to, format);
  }
}

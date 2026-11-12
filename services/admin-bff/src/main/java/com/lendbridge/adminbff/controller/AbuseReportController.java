package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.AbuseReportResponse;
import com.lendbridge.adminbff.messaging.AbuseReportGateway;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/messaging/abuse-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPPORT')")
public class AbuseReportController {

  private final AbuseReportGateway abuseReportGateway;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AbuseReportResponse>>> findOpen() {
    return ResponseEntity.ok(ApiResponse.success(abuseReportGateway.findOpen()));
  }

  @PatchMapping("/{reportId}/resolve")
  public ResponseEntity<ApiResponse<AbuseReportResponse>> resolve(@PathVariable UUID reportId) {
    return ResponseEntity.ok(ApiResponse.success(abuseReportGateway.resolve(reportId)));
  }
}

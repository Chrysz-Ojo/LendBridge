package com.lendbridge.messaging.controller;

import com.lendbridge.messaging.dto.AbuseReportRequest;
import com.lendbridge.messaging.dto.AbuseReportResponse;
import com.lendbridge.messaging.security.CurrentUser;
import com.lendbridge.messaging.service.AbuseReportService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PRD E9-S5: report button → admin queue. {@code @PreAuthorize("hasRole('SUPPORT')")} reuses
 * the existing {@code SUPPORT} role (authentication-service's {@code Role} enum), same precedent
 * as risk-service's {@code RISK_OFFICER}-gated endpoint and notification-service's
 * {@code SUPER_ADMIN}-gated {@code ChannelPolicyController}. */
@RestController
@RequiredArgsConstructor
public class AbuseReportController {

  private final AbuseReportService abuseReportService;

  @PostMapping("/api/v1/messaging/conversations/{conversationId}/reports")
  public ResponseEntity<ApiResponse<AbuseReportResponse>> file(
      @PathVariable UUID conversationId, @Valid @RequestBody AbuseReportRequest request, Authentication authentication) {
    UUID reporterUserId = CurrentUser.idFrom(authentication);
    return ResponseEntity.ok(ApiResponse.success(abuseReportService.file(conversationId, request, reporterUserId)));
  }

  @GetMapping("/api/v1/messaging/admin/reports")
  @PreAuthorize("hasRole('SUPPORT')")
  public ResponseEntity<ApiResponse<List<AbuseReportResponse>>> findOpen() {
    return ResponseEntity.ok(ApiResponse.success(abuseReportService.findOpen()));
  }

  @PatchMapping("/api/v1/messaging/admin/reports/{reportId}/resolve")
  @PreAuthorize("hasRole('SUPPORT')")
  public ResponseEntity<ApiResponse<AbuseReportResponse>> resolve(@PathVariable UUID reportId) {
    return ResponseEntity.ok(ApiResponse.success(abuseReportService.resolve(reportId)));
  }
}

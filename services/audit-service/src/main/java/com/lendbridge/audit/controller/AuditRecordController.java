package com.lendbridge.audit.controller;

import com.lendbridge.audit.dto.AuditRecordQueryRequest;
import com.lendbridge.audit.dto.AuditRecordResponse;
import com.lendbridge.audit.service.AuditRecordService;
import com.lendbridge.starter.dto.ApiResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The most sensitive read surface in the system — gated to {@code SUPER_ADMIN} alone, above
 * every other staff role (unlike risk-service's {@code RISK_OFFICER} or support-service's
 * {@code SUPPORT}), since this exposes a full cross-service history of state changes. */
@RestController
@RequestMapping("/api/v1/audit/records")
@RequiredArgsConstructor
public class AuditRecordController {

  private final AuditRecordService auditRecordService;

  @GetMapping
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<ApiResponse<List<AuditRecordResponse>>> query(
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) String aggregateKey,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "0") int limit) {
    AuditRecordQueryRequest request =
        AuditRecordQueryRequest.builder()
            .topic(topic)
            .aggregateKey(aggregateKey)
            .from(from)
            .to(to)
            .limit(limit)
            .build();
    return ResponseEntity.ok(ApiResponse.success(auditRecordService.query(request)));
  }
}

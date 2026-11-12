package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.audit.AuditQueryGateway;
import com.lendbridge.adminbff.dto.AuditRecordResponse;
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

@RestController
@RequestMapping("/api/v1/admin/audit/records")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AuditController {

  private final AuditQueryGateway auditQueryGateway;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AuditRecordResponse>>> query(
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) String aggregateKey,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(ApiResponse.success(auditQueryGateway.query(topic, aggregateKey, from, to, limit)));
  }
}

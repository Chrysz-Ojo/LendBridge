package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.ReconciliationRunResponse;
import com.lendbridge.adminbff.payment.ReconciliationGateway;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only passthrough to payment-service's reconciliation runs. Triggering a new run no longer
 * has a direct endpoint here — it goes through {@code ApprovalController} with
 * {@code actionType=RECONCILIATION_RUN}, since a reconciliation run touches real settlement data
 * and is worth a second {@code FINANCE} officer's sign-off (see ADR 0023). */
@RestController
@RequestMapping("/api/v1/admin/payments/reconciliation-runs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FINANCE')")
public class ReconciliationController {

  private final ReconciliationGateway reconciliationGateway;

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ReconciliationRunResponse>> getReconciliationRun(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(reconciliationGateway.getReconciliationRun(id)));
  }
}

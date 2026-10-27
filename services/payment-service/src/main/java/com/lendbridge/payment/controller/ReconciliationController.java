package com.lendbridge.payment.controller;

import com.lendbridge.payment.dto.ReconciliationRunRequest;
import com.lendbridge.payment.dto.ReconciliationRunResponse;
import com.lendbridge.payment.service.ReconciliationService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code @PreAuthorize("hasRole('FINANCE')")} reuses the precedent already established in
 * authentication-service's {@code AuthController}, not a new pattern. */
@RestController
@RequestMapping("/api/v1/payments/reconciliation-runs")
@RequiredArgsConstructor
public class ReconciliationController {

  private final ReconciliationService reconciliationService;

  @PostMapping
  @PreAuthorize("hasRole('FINANCE')")
  public ResponseEntity<ApiResponse<ReconciliationRunResponse>> runReconciliation(
      @Valid @RequestBody ReconciliationRunRequest request) {
    ReconciliationRunResponse response = reconciliationService.runReconciliation(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('FINANCE')")
  public ResponseEntity<ApiResponse<ReconciliationRunResponse>> getReconciliationRun(
      @PathVariable UUID id) {
    ReconciliationRunResponse response = reconciliationService.getReconciliationRun(id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

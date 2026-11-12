package com.lendbridge.adminbff.payment;

import com.lendbridge.adminbff.dto.ReconciliationRunRequest;
import com.lendbridge.adminbff.dto.ReconciliationRunResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationGateway {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationGateway.class);

  private final ReconciliationClient reconciliationClient;

  public ReconciliationGateway(ReconciliationClient reconciliationClient) {
    this.reconciliationClient = reconciliationClient;
  }

  /** Called only from {@code ApprovalServiceImpl} on approve of a {@code RECONCILIATION_RUN} —
   * never called directly from a passthrough controller endpoint, since triggering a run only
   * ever happens via the maker-checker approval flow (see ADR 0023). */
  @CircuitBreaker(name = "paymentService", fallbackMethod = "runReconciliationFallback")
  @Retry(name = "paymentService")
  public ReconciliationRunResponse runReconciliation(ReconciliationRunRequest request) {
    return reconciliationClient.runReconciliation(request).getData();
  }

  @SuppressWarnings("unused")
  private ReconciliationRunResponse runReconciliationFallback(ReconciliationRunRequest request, Throwable cause) {
    log.warn("payment-service unreachable for reconciliation run", cause);
    throw new DependencyUnavailableException("PAYMENT_SERVICE_UNAVAILABLE", "Unable to reach payment-service: " + cause.getMessage());
  }

  @CircuitBreaker(name = "paymentService", fallbackMethod = "getReconciliationRunFallback")
  @Retry(name = "paymentService")
  public ReconciliationRunResponse getReconciliationRun(UUID id) {
    return reconciliationClient.getReconciliationRun(id).getData();
  }

  @SuppressWarnings("unused")
  private ReconciliationRunResponse getReconciliationRunFallback(UUID id, Throwable cause) {
    log.warn("payment-service unreachable for reconciliation run {}", id, cause);
    throw new DependencyUnavailableException("PAYMENT_SERVICE_UNAVAILABLE", "Unable to reach payment-service: " + cause.getMessage());
  }
}

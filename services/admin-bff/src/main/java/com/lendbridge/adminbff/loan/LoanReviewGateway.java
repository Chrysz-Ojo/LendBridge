package com.lendbridge.adminbff.loan;

import com.lendbridge.adminbff.dto.LoanResponse;
import com.lendbridge.adminbff.dto.LoanReviewDecisionRequest;
import com.lendbridge.adminbff.dto.LoanReviewQueueItemResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoanReviewGateway {

  private static final Logger log = LoggerFactory.getLogger(LoanReviewGateway.class);

  private final LoanReviewClient loanReviewClient;

  public LoanReviewGateway(LoanReviewClient loanReviewClient) {
    this.loanReviewClient = loanReviewClient;
  }

  @CircuitBreaker(name = "loanService", fallbackMethod = "getQueueFallback")
  @Retry(name = "loanService")
  public List<LoanReviewQueueItemResponse> getQueue() {
    return loanReviewClient.getQueue().getData();
  }

  @SuppressWarnings("unused")
  private List<LoanReviewQueueItemResponse> getQueueFallback(Throwable cause) {
    log.warn("loan-service unreachable for review queue", cause);
    throw new DependencyUnavailableException("LOAN_SERVICE_UNAVAILABLE", "Unable to reach loan-service: " + cause.getMessage());
  }

  /** Called only from {@code ApprovalServiceImpl} on approve of a {@code LOAN_REVIEW_DECISION} —
   * never called directly from a passthrough controller endpoint, since deciding a flagged loan
   * only ever happens via the maker-checker approval flow (see ADR 0023). */
  @CircuitBreaker(name = "loanService", fallbackMethod = "decideFallback")
  @Retry(name = "loanService")
  public LoanResponse decide(UUID id, LoanReviewDecisionRequest request) {
    return loanReviewClient.decide(id, request).getData();
  }

  @SuppressWarnings("unused")
  private LoanResponse decideFallback(UUID id, LoanReviewDecisionRequest request, Throwable cause) {
    log.warn("loan-service unreachable for loan {}", id, cause);
    throw new DependencyUnavailableException("LOAN_SERVICE_UNAVAILABLE", "Unable to reach loan-service: " + cause.getMessage());
  }
}

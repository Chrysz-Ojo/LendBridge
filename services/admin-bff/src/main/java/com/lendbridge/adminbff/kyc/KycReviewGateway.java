package com.lendbridge.adminbff.kyc;

import com.lendbridge.adminbff.dto.KycReviewDecisionRequest;
import com.lendbridge.adminbff.dto.KycReviewQueueItemResponse;
import com.lendbridge.adminbff.dto.KycVerificationStatusResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** The only thing admin-bff's kyc-review controller calls to reach kyc-service — same
 * Resilience4j-on-the-gateway-not-the-client shape as kyc-service's own {@code DocumentGateway}. */
@Service
public class KycReviewGateway {

  private static final Logger log = LoggerFactory.getLogger(KycReviewGateway.class);

  private final KycReviewClient kycReviewClient;

  public KycReviewGateway(KycReviewClient kycReviewClient) {
    this.kycReviewClient = kycReviewClient;
  }

  @CircuitBreaker(name = "kycService", fallbackMethod = "getQueueFallback")
  @Retry(name = "kycService")
  public List<KycReviewQueueItemResponse> getQueue() {
    return kycReviewClient.getQueue().getData();
  }

  @SuppressWarnings("unused")
  private List<KycReviewQueueItemResponse> getQueueFallback(Throwable cause) {
    log.warn("kyc-service unreachable for review queue", cause);
    throw new DependencyUnavailableException("KYC_SERVICE_UNAVAILABLE", "Unable to reach kyc-service: " + cause.getMessage());
  }

  @CircuitBreaker(name = "kycService", fallbackMethod = "decideFallback")
  @Retry(name = "kycService")
  public KycVerificationStatusResponse decide(UUID caseId, KycReviewDecisionRequest request) {
    return kycReviewClient.decide(caseId, request).getData();
  }

  @SuppressWarnings("unused")
  private KycVerificationStatusResponse decideFallback(UUID caseId, KycReviewDecisionRequest request, Throwable cause) {
    log.warn("kyc-service unreachable for case {}", caseId, cause);
    throw new DependencyUnavailableException("KYC_SERVICE_UNAVAILABLE", "Unable to reach kyc-service: " + cause.getMessage());
  }
}

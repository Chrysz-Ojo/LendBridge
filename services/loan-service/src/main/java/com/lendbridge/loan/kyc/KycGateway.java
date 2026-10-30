package com.lendbridge.loan.kyc;

import com.lendbridge.starter.exception.DependencyUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only thing loan-service calls to reach kyc-service. Resilience4j annotations live here, not
 * on {@link KycClient} directly — same reasoning as kyc-service's own {@code DocumentGateway}.
 */
@Service
public class KycGateway {

  private static final Logger log = LoggerFactory.getLogger(KycGateway.class);

  private final KycClient kycClient;

  public KycGateway(KycClient kycClient) {
    this.kycClient = kycClient;
  }

  /**
   * A 404 from kyc-service means the caller has never started KYC verification — a legitimate
   * "not verified" business outcome (see kyc-service's {@code VerificationController}), not a
   * dependency failure, so it's handled here directly rather than via the Resilience4j fallback.
   */
  @CircuitBreaker(name = "kycService", fallbackMethod = "fetchFallback")
  @Retry(name = "kycService")
  public KycStatusResponse fetchMyStatus() {
    try {
      return kycClient.getMyStatus().getData();
    } catch (FeignException.NotFound e) {
      return new KycStatusResponse(null, null);
    }
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as fetchMyStatus's fallback
  private KycStatusResponse fetchFallback(Throwable cause) {
    log.warn("kyc-service unreachable while checking trust tier", cause);
    throw new DependencyUnavailableException(
        "KYC_SERVICE_UNAVAILABLE", "Unable to check KYC status: " + cause.getMessage());
  }
}

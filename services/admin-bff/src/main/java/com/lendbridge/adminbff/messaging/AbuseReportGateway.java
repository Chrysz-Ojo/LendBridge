package com.lendbridge.adminbff.messaging;

import com.lendbridge.adminbff.dto.AbuseReportResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AbuseReportGateway {

  private static final Logger log = LoggerFactory.getLogger(AbuseReportGateway.class);

  private final AbuseReportClient abuseReportClient;

  public AbuseReportGateway(AbuseReportClient abuseReportClient) {
    this.abuseReportClient = abuseReportClient;
  }

  @CircuitBreaker(name = "messagingService", fallbackMethod = "findOpenFallback")
  @Retry(name = "messagingService")
  public List<AbuseReportResponse> findOpen() {
    return abuseReportClient.findOpen().getData();
  }

  @SuppressWarnings("unused")
  private List<AbuseReportResponse> findOpenFallback(Throwable cause) {
    log.warn("messaging-service unreachable for abuse-report queue", cause);
    throw new DependencyUnavailableException("MESSAGING_SERVICE_UNAVAILABLE", "Unable to reach messaging-service: " + cause.getMessage());
  }

  @CircuitBreaker(name = "messagingService", fallbackMethod = "resolveFallback")
  @Retry(name = "messagingService")
  public AbuseReportResponse resolve(UUID reportId) {
    return abuseReportClient.resolve(reportId).getData();
  }

  @SuppressWarnings("unused")
  private AbuseReportResponse resolveFallback(UUID reportId, Throwable cause) {
    log.warn("messaging-service unreachable for report {}", reportId, cause);
    throw new DependencyUnavailableException("MESSAGING_SERVICE_UNAVAILABLE", "Unable to reach messaging-service: " + cause.getMessage());
  }
}

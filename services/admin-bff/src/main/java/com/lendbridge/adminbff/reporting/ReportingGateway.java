package com.lendbridge.adminbff.reporting;

import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ReportingGateway {

  private static final Logger log = LoggerFactory.getLogger(ReportingGateway.class);

  private final ReportingClient reportingClient;

  public ReportingGateway(ReportingClient reportingClient) {
    this.reportingClient = reportingClient;
  }

  @CircuitBreaker(name = "reportingService", fallbackMethod = "getFallback")
  @Retry(name = "reportingService")
  public ResponseEntity<byte[]> get(String reportName, Instant from, Instant to, String format) {
    return reportingClient.get(reportName, from, to, format);
  }

  @SuppressWarnings("unused")
  private ResponseEntity<byte[]> getFallback(String reportName, Instant from, Instant to, String format, Throwable cause) {
    log.warn("reporting-service unreachable for report {}", reportName, cause);
    throw new DependencyUnavailableException(
        "REPORTING_SERVICE_UNAVAILABLE", "Unable to reach reporting-service: " + cause.getMessage());
  }
}

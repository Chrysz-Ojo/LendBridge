package com.lendbridge.adminbff.audit;

import com.lendbridge.adminbff.dto.AuditRecordResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryGateway {

  private static final Logger log = LoggerFactory.getLogger(AuditQueryGateway.class);

  private final AuditQueryClient auditQueryClient;

  public AuditQueryGateway(AuditQueryClient auditQueryClient) {
    this.auditQueryClient = auditQueryClient;
  }

  @CircuitBreaker(name = "auditService", fallbackMethod = "queryFallback")
  @Retry(name = "auditService")
  public List<AuditRecordResponse> query(String topic, String aggregateKey, Instant from, Instant to, Integer limit) {
    return auditQueryClient.query(topic, aggregateKey, from, to, limit).getData();
  }

  @SuppressWarnings("unused")
  private List<AuditRecordResponse> queryFallback(
      String topic, String aggregateKey, Instant from, Instant to, Integer limit, Throwable cause) {
    log.warn("audit-service unreachable for query", cause);
    throw new DependencyUnavailableException("AUDIT_SERVICE_UNAVAILABLE", "Unable to reach audit-service: " + cause.getMessage());
  }
}

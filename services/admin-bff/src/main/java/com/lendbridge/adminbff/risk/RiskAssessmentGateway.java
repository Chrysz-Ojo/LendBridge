package com.lendbridge.adminbff.risk;

import com.lendbridge.adminbff.dto.RiskAssessmentDetailResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskAssessmentGateway {

  private static final Logger log = LoggerFactory.getLogger(RiskAssessmentGateway.class);

  private final RiskAssessmentClient riskAssessmentClient;

  public RiskAssessmentGateway(RiskAssessmentClient riskAssessmentClient) {
    this.riskAssessmentClient = riskAssessmentClient;
  }

  @CircuitBreaker(name = "riskService", fallbackMethod = "getDetailFallback")
  @Retry(name = "riskService")
  public RiskAssessmentDetailResponse getDetail(UUID loanId) {
    return riskAssessmentClient.getDetail(loanId).getData();
  }

  @SuppressWarnings("unused")
  private RiskAssessmentDetailResponse getDetailFallback(UUID loanId, Throwable cause) {
    log.warn("risk-service unreachable for loan {}", loanId, cause);
    throw new DependencyUnavailableException("RISK_SERVICE_UNAVAILABLE", "Unable to reach risk-service: " + cause.getMessage());
  }
}

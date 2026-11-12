package com.lendbridge.adminbff.supportfaq;

import com.lendbridge.adminbff.dto.FaqRequest;
import com.lendbridge.adminbff.dto.FaqResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SupportFaqGateway {

  private static final Logger log = LoggerFactory.getLogger(SupportFaqGateway.class);

  private final SupportFaqClient supportFaqClient;

  public SupportFaqGateway(SupportFaqClient supportFaqClient) {
    this.supportFaqClient = supportFaqClient;
  }

  @CircuitBreaker(name = "supportService", fallbackMethod = "createFallback")
  @Retry(name = "supportService")
  public FaqResponse create(FaqRequest request) {
    return supportFaqClient.create(request).getData();
  }

  @SuppressWarnings("unused")
  private FaqResponse createFallback(FaqRequest request, Throwable cause) {
    log.warn("support-service unreachable for FAQ create", cause);
    throw new DependencyUnavailableException("SUPPORT_SERVICE_UNAVAILABLE", "Unable to reach support-service: " + cause.getMessage());
  }

  @CircuitBreaker(name = "supportService", fallbackMethod = "updateFallback")
  @Retry(name = "supportService")
  public FaqResponse update(UUID faqId, FaqRequest request) {
    return supportFaqClient.update(faqId, request).getData();
  }

  @SuppressWarnings("unused")
  private FaqResponse updateFallback(UUID faqId, FaqRequest request, Throwable cause) {
    log.warn("support-service unreachable for FAQ {}", faqId, cause);
    throw new DependencyUnavailableException("SUPPORT_SERVICE_UNAVAILABLE", "Unable to reach support-service: " + cause.getMessage());
  }

  @CircuitBreaker(name = "supportService", fallbackMethod = "deleteFallback")
  @Retry(name = "supportService")
  public void delete(UUID faqId) {
    supportFaqClient.delete(faqId);
  }

  @SuppressWarnings("unused")
  private void deleteFallback(UUID faqId, Throwable cause) {
    log.warn("support-service unreachable for FAQ {} delete", faqId, cause);
    throw new DependencyUnavailableException("SUPPORT_SERVICE_UNAVAILABLE", "Unable to reach support-service: " + cause.getMessage());
  }
}

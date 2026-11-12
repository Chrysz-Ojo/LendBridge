package com.lendbridge.adminbff.supportticket;

import com.lendbridge.adminbff.dto.AdminTicketResponse;
import com.lendbridge.adminbff.dto.UpdateTicketStatusRequest;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Circuit breaker instance name {@code supportService} is shared with {@code SupportFaqGateway} —
 * both target the same actual backend (support-service), so resilience state is tracked per real
 * downstream dependency, not per local integration package. */
@Service
public class SupportTicketGateway {

  private static final Logger log = LoggerFactory.getLogger(SupportTicketGateway.class);

  private final SupportTicketClient supportTicketClient;

  public SupportTicketGateway(SupportTicketClient supportTicketClient) {
    this.supportTicketClient = supportTicketClient;
  }

  @CircuitBreaker(name = "supportService", fallbackMethod = "findForAdminQueueFallback")
  @Retry(name = "supportService")
  public List<AdminTicketResponse> findForAdminQueue() {
    return supportTicketClient.findForAdminQueue().getData();
  }

  @SuppressWarnings("unused")
  private List<AdminTicketResponse> findForAdminQueueFallback(Throwable cause) {
    log.warn("support-service unreachable for ticket queue", cause);
    throw new DependencyUnavailableException("SUPPORT_SERVICE_UNAVAILABLE", "Unable to reach support-service: " + cause.getMessage());
  }

  @CircuitBreaker(name = "supportService", fallbackMethod = "updateStatusFallback")
  @Retry(name = "supportService")
  public AdminTicketResponse updateStatus(UUID ticketId, UpdateTicketStatusRequest request) {
    return supportTicketClient.updateStatus(ticketId, request).getData();
  }

  @SuppressWarnings("unused")
  private AdminTicketResponse updateStatusFallback(UUID ticketId, UpdateTicketStatusRequest request, Throwable cause) {
    log.warn("support-service unreachable for ticket {}", ticketId, cause);
    throw new DependencyUnavailableException("SUPPORT_SERVICE_UNAVAILABLE", "Unable to reach support-service: " + cause.getMessage());
  }
}

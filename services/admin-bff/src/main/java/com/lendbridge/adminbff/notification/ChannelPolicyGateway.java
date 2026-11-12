package com.lendbridge.adminbff.notification;

import com.lendbridge.adminbff.dto.ChannelPolicyRequest;
import com.lendbridge.adminbff.dto.ChannelPolicyResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChannelPolicyGateway {

  private static final Logger log = LoggerFactory.getLogger(ChannelPolicyGateway.class);

  private final ChannelPolicyClient channelPolicyClient;

  public ChannelPolicyGateway(ChannelPolicyClient channelPolicyClient) {
    this.channelPolicyClient = channelPolicyClient;
  }

  @CircuitBreaker(name = "notificationService", fallbackMethod = "listAllFallback")
  @Retry(name = "notificationService")
  public List<ChannelPolicyResponse> listAll() {
    return channelPolicyClient.listAll().getData();
  }

  @SuppressWarnings("unused")
  private List<ChannelPolicyResponse> listAllFallback(Throwable cause) {
    log.warn("notification-service unreachable for channel policies", cause);
    throw new DependencyUnavailableException(
        "NOTIFICATION_SERVICE_UNAVAILABLE", "Unable to reach notification-service: " + cause.getMessage());
  }

  /** Called only from {@code ApprovalServiceImpl} on approve of a {@code CHANNEL_POLICY_UPDATE} —
   * never called directly from a passthrough controller endpoint, since updating a channel
   * policy only ever happens via the maker-checker approval flow (see ADR 0023). */
  @CircuitBreaker(name = "notificationService", fallbackMethod = "upsertFallback")
  @Retry(name = "notificationService")
  public ChannelPolicyResponse upsert(String eventType, ChannelPolicyRequest request) {
    return channelPolicyClient.upsert(eventType, request).getData();
  }

  @SuppressWarnings("unused")
  private ChannelPolicyResponse upsertFallback(String eventType, ChannelPolicyRequest request, Throwable cause) {
    log.warn("notification-service unreachable for channel policy {}", eventType, cause);
    throw new DependencyUnavailableException(
        "NOTIFICATION_SERVICE_UNAVAILABLE", "Unable to reach notification-service: " + cause.getMessage());
  }
}

package com.lendbridge.adminbff.authentication;

import com.lendbridge.adminbff.dto.RoleAssignmentRequest;
import com.lendbridge.adminbff.dto.RoleListResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** The one gateway {@code ApprovalServiceImpl} calls on approve — never called directly from a
 * passthrough controller endpoint, since role assignment only ever happens via the maker-checker
 * approval flow (see ADR 0023). */
@Service
public class RoleAssignmentGateway {

  private static final Logger log = LoggerFactory.getLogger(RoleAssignmentGateway.class);

  private final RoleAssignmentClient roleAssignmentClient;

  public RoleAssignmentGateway(RoleAssignmentClient roleAssignmentClient) {
    this.roleAssignmentClient = roleAssignmentClient;
  }

  @CircuitBreaker(name = "authenticationService", fallbackMethod = "assignRoleFallback")
  @Retry(name = "authenticationService")
  public RoleListResponse assignRole(UUID targetUserId, RoleAssignmentRequest request) {
    return roleAssignmentClient.assignRole(targetUserId, request).getData();
  }

  @SuppressWarnings("unused")
  private RoleListResponse assignRoleFallback(UUID targetUserId, RoleAssignmentRequest request, Throwable cause) {
    log.warn("authentication-service unreachable assigning role to {}", targetUserId, cause);
    throw new DependencyUnavailableException(
        "AUTHENTICATION_SERVICE_UNAVAILABLE", "Unable to reach authentication-service: " + cause.getMessage());
  }
}

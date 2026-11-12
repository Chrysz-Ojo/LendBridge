package com.lendbridge.adminbff.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

  private CurrentUser() {}

  public static UUID idFrom(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new IllegalStateException("Authentication principal is not a JWT");
    }
    return UUID.fromString(jwt.getSubject());
  }

  /** Whether the caller holds the given staff role — used by {@code ApprovalServiceImpl} to
   * enforce each {@code ApprovalActionType}'s specific required role, since
   * {@code ApprovalController} itself only gates on "any staff role" generically. */
  public static boolean hasRole(Authentication authentication, String role) {
    String authority = "ROLE_" + role;
    return authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }
}

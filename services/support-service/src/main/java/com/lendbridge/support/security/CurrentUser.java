package com.lendbridge.support.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

  private static final String SUPPORT_AUTHORITY = "ROLE_SUPPORT";

  private CurrentUser() {}

  public static UUID idFrom(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new IllegalStateException("Authentication principal is not a JWT");
    }
    return UUID.fromString(jwt.getSubject());
  }

  /** Whether the caller holds the {@code SUPPORT} staff role — used to grant ticket access beyond
   * plain ownership (view/reply on any ticket, admin queue, FAQ management), same shape as every
   * other service's {@code @PreAuthorize("hasRole('...')")} checks, but computed inline where a
   * single endpoint must branch between "owner" and "staff" behavior rather than reject outright. */
  public static boolean isSupportStaff(Authentication authentication) {
    return authentication.getAuthorities().stream().anyMatch(a -> SUPPORT_AUTHORITY.equals(a.getAuthority()));
  }
}

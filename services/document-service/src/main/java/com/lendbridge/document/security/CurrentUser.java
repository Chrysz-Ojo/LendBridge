package com.lendbridge.document.security;

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
}

package com.lendbridge.messaging.security;

import java.security.Principal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** The STOMP session's {@link Principal} once {@link StompAuthChannelInterceptor} has validated
 * the CONNECT frame's JWT — {@code @MessageMapping} handlers read {@link #getUserId()} off this. */
@Getter
@AllArgsConstructor
public class StompPrincipal implements Principal {

  private final UUID userId;

  @Override
  public String getName() {
    return userId.toString();
  }
}

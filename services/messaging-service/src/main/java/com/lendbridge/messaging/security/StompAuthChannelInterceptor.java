package com.lendbridge.messaging.security;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Browser STOMP clients (e.g. stomp.js) can set a native "Authorization" header on the CONNECT
 * frame even though the raw WebSocket handshake itself can't carry custom headers — so JWT auth
 * happens here, at the STOMP protocol level, not via Spring Security's HTTP filter chain (the
 * gateway and this service's own {@code SecurityConfig} both permit {@code /ws/**} at the HTTP
 * layer for exactly this reason). Validates against the same {@code JwtDecoder} every other
 * service's REST endpoints already use (same jwk-set-uri).
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private final JwtDecoder jwtDecoder;

  @Override
  public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
      List<String> authHeaders = accessor.getNativeHeader("Authorization");
      String token = extractBearerToken(authHeaders);
      Jwt jwt = jwtDecoder.decode(token);
      accessor.setUser(new StompPrincipal(UUID.fromString(jwt.getSubject())));
    }
    return message;
  }

  private static String extractBearerToken(List<String> authHeaders) {
    if (authHeaders == null || authHeaders.isEmpty()) {
      throw new JwtException("Missing Authorization header on STOMP CONNECT frame");
    }
    String header = authHeaders.get(0);
    if (!header.startsWith("Bearer ")) {
      throw new JwtException("Authorization header on STOMP CONNECT frame must be a Bearer token");
    }
    return header.substring("Bearer ".length());
  }
}

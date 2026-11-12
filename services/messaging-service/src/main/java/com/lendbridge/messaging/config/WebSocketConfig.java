package com.lendbridge.messaging.config;

import com.lendbridge.messaging.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * No SockJS fallback — PRD's stack is plain "STOMP over WebSocket" and AWS ALB (the chosen
 * ingress) supports WebSocket upgrades natively, so there's no proxy constraint forcing a
 * long-polling fallback. {@code enableSimpleBroker} only delivers to sessions connected to *this*
 * pod; cross-pod fan-out is the home-grown Redis bridge in the {@code realtime} package, not
 * {@code enableStompBrokerRelay} (which expects a real STOMP-speaking broker like RabbitMQ).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

  @Value("${messaging.websocket.allowed-origins:http://localhost:3000}")
  private String allowedOrigins;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.split(","));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompAuthChannelInterceptor);
  }
}

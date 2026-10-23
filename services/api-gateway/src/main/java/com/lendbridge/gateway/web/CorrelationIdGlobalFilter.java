package com.lendbridge.gateway.web;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stamps every request with {@code X-Correlation-Id} (generating one if the caller didn't send
 * one) before it's routed downstream, and echoes it back on the response. Kept local to this
 * module rather than in platform-starter: that module pulls in spring-boot-starter-web
 * (servlet), which Spring Cloud Gateway cannot coexist with on the classpath — it breaks
 * reactive web application type detection and the gateway fails to start.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

  public static final String HEADER = "X-Correlation-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }
    String finalCorrelationId = correlationId;

    ServerHttpRequest mutatedRequest =
        exchange.getRequest().mutate().header(HEADER, finalCorrelationId).build();
    exchange.getResponse().getHeaders().add(HEADER, finalCorrelationId);

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}

package com.lendbridge.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimiterConfigTest {

  private final KeyResolver resolver = new RateLimiterConfig().ipKeyResolver();

  @Test
  void keysByTheCallersRemoteAddress() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/anything")
                .remoteAddress(new java.net.InetSocketAddress("203.0.113.7", 54321)));

    String key = resolver.resolve(exchange).block();

    assertThat(key).isEqualTo("203.0.113.7");
  }

  @Test
  void fallsBackToUnknownWhenThereIsNoRemoteAddress() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/anything"));

    String key = resolver.resolve(exchange).block();

    assertThat(key).isEqualTo("unknown");
  }
}

package com.lendbridge.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.gateway.support.AbstractGatewayIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Separate test class so its tight rate-limit config doesn't affect {@link GatewayRoutingIT}'s
 * Spring context (different @DynamicPropertySource values give it its own cached context). */
class RateLimitIT extends AbstractGatewayIntegrationTest {

  @DynamicPropertySource
  static void tightRateLimit(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.gateway.routes[0].filters[0].args[redis-rate-limiter.replenishRate]",
        () -> "1");
    registry.add(
        "spring.cloud.gateway.routes[0].filters[0].args[redis-rate-limiter.burstCapacity]",
        () -> "2");
  }

  @Test
  void exceedingTheConfiguredBurstReturnsTooManyRequests() {
    boolean sawTooManyRequests = false;
    for (int i = 0; i < 10 && !sawTooManyRequests; i++) {
      HttpStatus status =
          (HttpStatus)
              webTestClient
                  .post()
                  .uri("/api/v1/auth/login")
                  .exchange()
                  .returnResult(Void.class)
                  .getStatus();
      if (status == HttpStatus.TOO_MANY_REQUESTS) {
        sawTooManyRequests = true;
      }
    }
    assertThat(sawTooManyRequests).isTrue();
  }
}

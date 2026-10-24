package com.lendbridge.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.gateway.support.AbstractGatewayIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpStatus;

/**
 * Mutates the real {@link RedisRateLimiter}'s per-route {@code Config} map directly to tighten
 * the limit for this test, rather than trying to override application.yml's route definition via
 * properties — see the note on {@link AbstractGatewayIntegrationTest#registerProperties} for why
 * that path is a dead end. This Config map is RedisRateLimiter's own public extension point.
 * Config is restored after the test since this shares a cached Spring context with {@link
 * GatewayRoutingIT}.
 */
class RateLimitIT extends AbstractGatewayIntegrationTest {

  private static final String ROUTE_ID = "authentication-service";

  @Autowired private RedisRateLimiter redisRateLimiter;
  @Autowired private ReactiveRedisConnectionFactory redisConnectionFactory;

  private RedisRateLimiter.Config originalConfig;

  @BeforeEach
  void tightenRateLimit() {
    originalConfig = redisRateLimiter.getConfig().get(ROUTE_ID);
    redisRateLimiter
        .getConfig()
        .put(ROUTE_ID, new RedisRateLimiter.Config().setReplenishRate(1).setBurstCapacity(2));
  }

  @AfterEach
  void restoreRateLimit() {
    if (originalConfig != null) {
      redisRateLimiter.getConfig().put(ROUTE_ID, originalConfig);
    } else {
      redisRateLimiter.getConfig().remove(ROUTE_ID);
    }
    // Restoring the Config above resets the *limit*, not the token count this test already
    // drained to 0 in Redis -- without this, GatewayRoutingIT (sharing this same Testcontainers
    // Redis and the same resolved IP key) gets 429s on its very first requests too, since the
    // bucket has barely had time to refill. This is a disposable test-only Redis, so flushing
    // everything is simplest and safe.
    redisConnectionFactory.getReactiveConnection().serverCommands().flushDb().block();
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

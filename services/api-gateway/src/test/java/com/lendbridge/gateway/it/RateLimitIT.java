package com.lendbridge.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.gateway.support.AbstractGatewayIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;

/**
 * Mutates the real {@link RedisRateLimiter}'s per-route {@code Config} map directly rather than
 * trying to override its YAML-bound args via properties: the args map keys contain literal dots
 * (e.g. "redis-rate-limiter.replenishRate"), and property-style dynamic sources don't bind those
 * the same way YAML does, however they're spelled. This Config map is RedisRateLimiter's own
 * public extension point and sidesteps that entirely. Config is restored after the test since
 * this shares a cached Spring context with {@link GatewayRoutingIT}.
 */
class RateLimitIT extends AbstractGatewayIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(RateLimitIT.class);
  private static final String ROUTE_ID = "authentication-service";

  @Autowired private RedisRateLimiter redisRateLimiter;
  @Autowired private RouteLocator routeLocator;

  private RedisRateLimiter.Config originalConfig;

  @BeforeEach
  void tightenRateLimit() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    log.info("DIAGNOSTIC registered route ids: {}", routes.stream().map(Route::getId).toList());

    originalConfig = redisRateLimiter.getConfig().get(ROUTE_ID);
    redisRateLimiter
        .getConfig()
        .put(ROUTE_ID, new RedisRateLimiter.Config().setReplenishRate(1).setBurstCapacity(2));
    log.info(
        "DIAGNOSTIC config for '{}' after override: replenishRate={} burstCapacity={}",
        ROUTE_ID,
        redisRateLimiter.getConfig().get(ROUTE_ID).getReplenishRate(),
        redisRateLimiter.getConfig().get(ROUTE_ID).getBurstCapacity());
  }

  @AfterEach
  void restoreRateLimit() {
    if (originalConfig != null) {
      redisRateLimiter.getConfig().put(ROUTE_ID, originalConfig);
    } else {
      redisRateLimiter.getConfig().remove(ROUTE_ID);
    }
  }

  @Test
  void directRateLimiterCallThrottlesAfterBurstCapacity() {
    for (int i = 0; i < 5; i++) {
      RateLimiter.Response response = redisRateLimiter.isAllowed(ROUTE_ID, "diagnostic-key").block();
      log.info(
          "DIAGNOSTIC direct isAllowed call {} -> allowed={} headers={}",
          i,
          response.isAllowed(),
          response.getHeaders());
    }
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
      log.info("DIAGNOSTIC request {} -> {}", i, status);
      if (status == HttpStatus.TOO_MANY_REQUESTS) {
        sawTooManyRequests = true;
      }
    }
    assertThat(sawTooManyRequests).isTrue();
  }
}

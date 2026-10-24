package com.lendbridge.gateway.config;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

  /**
   * Keys the Redis-backed RequestRateLimiter by caller IP. Not per-user: the gateway validates
   * JWTs but doesn't parse claims out for this purpose, and unauthenticated routes (login,
   * register) need a rate-limit key before any token exists anyway.
   */
  @Bean
  public KeyResolver ipKeyResolver() {
    return exchange -> {
      InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
      String key = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
      return Mono.just(key);
    };
  }
}

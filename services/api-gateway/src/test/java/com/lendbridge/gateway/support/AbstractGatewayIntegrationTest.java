package com.lendbridge.gateway.support;

import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared fixture: Redis and the authentication-service stub are both JVM-lifetime singletons,
 * started once in a static initializer and never stopped mid-suite. This field is declared once
 * here and shared by every subclass (RateLimitIT, GatewayRoutingIT, ...) — starting/stopping it
 * per subclass via @BeforeAll/@AfterAll broke as soon as two subclasses became cache-equivalent
 * (identical @DynamicPropertySource values) and started sharing one Spring context: whichever
 * subclass's @AfterAll ran first killed the stub out from under the other's already-built
 * ReactiveJwtDecoder, which had its JWKS URI baked in at context-creation time. Same lesson as
 * authentication-service's AbstractIntegrationTest: don't tie a shared static fixture's lifecycle
 * to any one subclass's test lifecycle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
public abstract class AbstractGatewayIntegrationTest {

  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  protected static final AuthServiceStub authServiceStub = AuthServiceStub.start();

  static {
    REDIS.start();
  }

  /**
   * Only overrides id/uri/predicates here — the route's {@code filters} (the RequestRateLimiter
   * config) are left to merge from application.yml untouched. Mixing dynamic-property keys under
   * {@code routes[0].filters[...]} with the YAML-defined ones was tried and reliably broke
   * binding validation (sibling fields of the same indexed list element don't merge cleanly
   * across different property sources). RateLimitIT tunes the rate limiter in Java instead, via
   * {@code RedisRateLimiter}'s own per-route Config map.
   */
  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.cloud.gateway.routes[0].id", () -> "authentication-service");
    registry.add("spring.cloud.gateway.routes[0].uri", () -> authServiceStub.baseUrl());
    registry.add(
        "spring.cloud.gateway.routes[0].predicates[0]",
        () -> "Path=/api/v1/auth/**,/.well-known/jwks.json");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> authServiceStub.jwksUrl());
  }

  @org.springframework.beans.factory.annotation.Autowired protected WebTestClient webTestClient;
}

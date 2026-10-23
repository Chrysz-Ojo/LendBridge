package com.lendbridge.gateway.support;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared fixture: Redis (singleton, started manually — see the note on why {@code @Container}/
 * {@code @Testcontainers} must NOT be used here, in authentication-service's
 * AbstractIntegrationTest) plus a stub standing in for authentication-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
public abstract class AbstractGatewayIntegrationTest {

  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static {
    REDIS.start();
  }

  protected static AuthServiceStub authServiceStub;

  @BeforeAll
  static void startStub() {
    authServiceStub = AuthServiceStub.start();
  }

  @AfterAll
  static void stopStub() {
    authServiceStub.stop();
  }

  /**
   * Registers the whole route definition here rather than letting it merge with
   * application.yml's: Spring Cloud Gateway's relaxed binding for {@code
   * GatewayProperties.routes[0]} doesn't reliably merge sibling fields (e.g. {@code
   * filters[0].name}) across different property sources — once any key under an indexed list
   * element comes from a higher-precedence source, sibling keys from a lower one can go missing
   * and binding fails validation. Keeping every field for routes[0] in this one dynamic property
   * source (subclasses override just the numeric rate-limit args) avoids that entirely.
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
    registry.add("spring.cloud.gateway.routes[0].filters[0].name", () -> "RequestRateLimiter");
    // Bracket notation, not dotted: the key itself contains a dot ("redis-rate-limiter.
    // replenishRate"), and from a property-style source (unlike YAML) a plain dotted suffix
    // after a Map<String,String> property is read as further nesting, not a literal map key.
    registry.add(
        "spring.cloud.gateway.routes[0].filters[0].args[redis-rate-limiter.replenishRate]",
        () -> "20");
    registry.add(
        "spring.cloud.gateway.routes[0].filters[0].args[redis-rate-limiter.burstCapacity]",
        () -> "40");
    registry.add(
        "spring.cloud.gateway.routes[0].filters[0].args[key-resolver]",
        () -> "#{@ipKeyResolver}");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> authServiceStub.jwksUrl());
  }

  @org.springframework.beans.factory.annotation.Autowired protected WebTestClient webTestClient;
}

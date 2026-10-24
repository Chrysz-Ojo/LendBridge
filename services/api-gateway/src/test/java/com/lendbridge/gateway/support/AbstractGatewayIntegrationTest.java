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
   * Overrides only the env-var placeholders application.yml already defines for exactly this
   * purpose (${AUTH_SERVICE_URI}, ${AUTH_SERVICE_JWK_SET_URI}, ${REDIS_HOST}, ${REDIS_PORT}) —
   * never routes[0].* paths directly. Overriding routes[0].id/uri/predicates directly (tried
   * first) silently drops the route's filters: the doubly-nested routes[0].filters[0].* never
   * binds from application.yml once anything else under routes[0] comes from a dynamic property
   * source, with no error — confirmed by logging Route.getFilters() and seeing []. Placeholder
   * substitution happens at the Environment level before GatewayProperties binding even starts,
   * so application.yml stays the sole, uncontested source for the whole route (filters included)
   * and only the ${...} values change.
   */
  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("REDIS_HOST", REDIS::getHost);
    registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
    registry.add("AUTH_SERVICE_URI", () -> authServiceStub.baseUrl());
    registry.add("AUTH_SERVICE_JWK_SET_URI", () -> authServiceStub.jwksUrl());
  }

  @org.springframework.beans.factory.annotation.Autowired protected WebTestClient webTestClient;
}

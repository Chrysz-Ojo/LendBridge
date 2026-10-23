package com.lendbridge.gateway.it;

import com.lendbridge.gateway.support.AbstractGatewayIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GatewayRoutingIT extends AbstractGatewayIntegrationTest {

  @Test
  void publicAuthRouteIsProxiedWithoutAToken() {
    webTestClient
        .post()
        .uri("/api/v1/auth/login")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.stub")
        .isEqualTo("ok")
        .jsonPath("$.path")
        .isEqualTo("/api/v1/auth/login");
  }

  @Test
  void protectedRouteWithoutATokenIsRejectedByTheGateway() {
    webTestClient.get().uri("/api/v1/auth/me").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void protectedRouteWithAValidTokenIsProxied() {
    String token = authServiceStub.signToken("user-123", List.of("BORROWER"), 300);

    webTestClient
        .get()
        .uri("/api/v1/auth/me")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.OK)
        .expectBody()
        .jsonPath("$.stub")
        .isEqualTo("ok");
  }

  @Test
  void expiredTokenIsRejected() {
    String token = authServiceStub.signToken("user-123", List.of("BORROWER"), -10);

    webTestClient
        .get()
        .uri("/api/v1/auth/me")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }
}

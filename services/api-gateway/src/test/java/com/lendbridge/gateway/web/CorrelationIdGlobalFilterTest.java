package com.lendbridge.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationIdGlobalFilterTest {

  private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

  @Test
  void generatesACorrelationIdWhenTheCallerDidNotSendOne() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/anything"));
    String[] seenByDownstream = new String[1];
    GatewayFilterChain chain =
        ex -> {
          seenByDownstream[0] = ex.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER);
          return Mono.empty();
        };

    filter.filter(exchange, chain).block();

    assertThat(seenByDownstream[0]).isNotBlank();
    assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER))
        .isEqualTo(seenByDownstream[0]);
  }

  @Test
  void preservesAnIncomingCorrelationId() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/anything")
                .header(CorrelationIdGlobalFilter.HEADER, "caller-supplied-id"));
    String[] seenByDownstream = new String[1];
    GatewayFilterChain chain =
        (ServerWebExchange ex) -> {
          seenByDownstream[0] = ex.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER);
          return Mono.empty();
        };

    filter.filter(exchange, chain).block();

    assertThat(seenByDownstream[0]).isEqualTo("caller-supplied-id");
    assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER))
        .isEqualTo("caller-supplied-id");
  }
}

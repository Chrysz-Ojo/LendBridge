package com.lendbridge.wallet.ledger;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the inbound request's own {@code Authorization} header onto the outgoing call to
 * ledger-service. There is no service-to-service (client-credentials/m2m) auth mechanism
 * anywhere in this repo yet, so this only works because every wallet-service endpoint that calls
 * Ledger runs inside an end-user-authenticated HTTP request, and ledger-service's resource-server
 * config only validates that a token is validly signed — not who it belongs to.
 */
@Configuration
public class LedgerFeignConfig {

  @Bean
  public RequestInterceptor authorizationForwardingInterceptor() {
    return requestTemplate -> {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes == null) {
        return;
      }
      String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
      if (authorization != null) {
        requestTemplate.header(HttpHeaders.AUTHORIZATION, authorization);
      }
    };
  }
}

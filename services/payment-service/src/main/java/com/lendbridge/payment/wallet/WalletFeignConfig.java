package com.lendbridge.payment.wallet;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Forwards the inbound request's own {@code Authorization} header onto the outgoing call to
 * wallet-service — the same pattern wallet-service's own {@code LedgerFeignConfig} uses for
 * calling ledger-service. Works because every payment-service endpoint that calls wallet-service
 * runs inside an end-user-authenticated HTTP request. */
@Configuration
public class WalletFeignConfig {

  @Bean
  public RequestInterceptor walletAuthorizationForwardingInterceptor() {
    return requestTemplate -> {
      ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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

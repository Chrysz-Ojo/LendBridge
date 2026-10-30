package com.lendbridge.investment.wallet;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Forwards the inbound request's own {@code Authorization} header onto the outgoing call to
 * wallet-service — the lender's own JWT, since committing funds is lender-initiated (there is no
 * cross-service auth gap here, unlike marketplace-service's blocked trust-tier lookup:
 * docs/adr/0017). Same pattern as payment-service's own {@code WalletFeignConfig}.
 *
 * <p>Deliberately NOT {@code @Configuration}: only referenced via
 * {@code @FeignClient(configuration = WalletFeignConfig.class)}, never component-scanned as a
 * global interceptor (guarded by {@code FeignConfigurationIsolationIT}). */
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

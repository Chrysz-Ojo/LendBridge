package com.lendbridge.loan.kyc;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the inbound request's own {@code Authorization} header onto the outgoing call to
 * kyc-service — same pattern as kyc-service's own {@code DocumentFeignConfig} and payment-service's
 * {@code WalletFeignConfig}. Works because every loan-service endpoint that calls kyc-service runs
 * inside an end-user-authenticated HTTP request, and kyc-service's {@code /verifications/me}
 * endpoint scopes to the caller's own JWT subject.
 *
 * <p>Deliberately NOT {@code @Configuration} — only referenced via
 * {@code @FeignClient(configuration = KycFeignConfig.class)}, never component-scanned as a global
 * interceptor (see {@code FeignConfigurationIsolationIT}).
 */
public class KycFeignConfig {

  @Bean
  public RequestInterceptor kycAuthorizationForwardingInterceptor() {
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

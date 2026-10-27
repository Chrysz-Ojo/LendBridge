package com.lendbridge.payment.authentication;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Forwards the inbound request's own {@code Authorization} header — a withdrawal request is
 * itself an authenticated end-user call, so forwarding that same JWT to authentication-service's
 * step-up endpoint works exactly like {@code WalletFeignConfig} does for wallet-service.
 *
 * <p>Deliberately NOT {@code @Configuration} — see {@code PaystackFeignConfig}'s javadoc for why:
 * only referenced via {@code @FeignClient(configuration = AuthFeignConfig.class)}, never
 * component-scanned as a global interceptor. */
public class AuthFeignConfig {

  @Bean
  public RequestInterceptor authAuthorizationForwardingInterceptor() {
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

package com.lendbridge.adminbff.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the inbound request's own {@code Authorization} header onto every outgoing Feign call
 * to a backend LendBridge service — same pattern as kyc-service's {@code DocumentFeignConfig}.
 * admin-bff never acts as its own trusted identity: every one of its 11 integrations calls a
 * backend on behalf of the staff member whose JWT is already on the inbound request, so that
 * backend's own {@code @PreAuthorize} independently re-enforces the real role check — true
 * defense in depth, zero new trust granted.
 *
 * <p>Deliberately NOT {@code @Configuration} — only referenced via each
 * {@code @FeignClient(configuration = FeignAuthForwardingConfig.class)}. Every one of this
 * service's 11 Feign clients happens to target an internal LendBridge service today, so a global
 * interceptor would be harmless right now — but following kyc-service's own per-client-scoped
 * convention here means a future external-vendor Feign client (if one is ever added) can't
 * accidentally inherit this and leak a staff member's bearer token to a third party.
 */
public class FeignAuthForwardingConfig {

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

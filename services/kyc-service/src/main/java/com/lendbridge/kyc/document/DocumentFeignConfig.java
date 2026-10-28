package com.lendbridge.kyc.document;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the inbound request's own {@code Authorization} header onto the outgoing call to
 * document-service — same pattern and same caveat as wallet-service's {@code LedgerFeignConfig}:
 * there is no service-to-service (client-credentials/m2m) auth mechanism anywhere in this repo
 * yet, so this only works because every kyc-service endpoint that calls document-service runs
 * inside an end-user-authenticated HTTP request, and document-service scopes a document lookup to
 * the caller's own JWT subject (its {@code findByIdAndOwnerId}). A KYC officer's own JWT is not the
 * borrower's, so {@code ReviewController} can never live-fetch a borrower's documents this way —
 * see {@code VerificationCase.documentSnapshot} and ADR 0014 for how the review queue works around
 * that gap.
 *
 * <p>Deliberately NOT {@code @Configuration} — only referenced via
 * {@code @FeignClient(configuration = DocumentFeignConfig.class)}, for the exact reason explained
 * in payment-service's {@code PaystackFeignConfig}: unlike wallet-service (which only ever talks to
 * one other internal service), kyc-service also has three external-provider Feign clients
 * (Smile ID, Dojah, Prembly) — if this bean were registered globally via the broad
 * {@code @ComponentScan(basePackages = "com.lendbridge")}, Spring Cloud OpenFeign would apply its
 * Authorization-forwarding interceptor to every Feign client, including those, forwarding the
 * end-user's own bearer token to an external KYC vendor API instead of that vendor's own API key.
 * {@code FeignConfigurationIsolationIT} guards against exactly this regression.
 */
public class DocumentFeignConfig {

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

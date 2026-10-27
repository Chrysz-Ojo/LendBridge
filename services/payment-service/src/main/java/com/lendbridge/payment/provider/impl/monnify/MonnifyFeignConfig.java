package com.lendbridge.payment.provider.impl.monnify;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/** Deliberately NOT {@code @Configuration} — see {@code PaystackFeignConfig}'s javadoc for why:
 * only referenced via {@code @FeignClient(configuration = MonnifyFeignConfig.class)}, never
 * component-scanned as a global interceptor. This one matters most of the five: its interceptor
 * makes a real network call ({@code tokenProvider.getAccessToken()}), so leaking it onto every
 * other Feign client (as happened before this fix) breaks even calls that have nothing to do
 * with Monnify. */
public class MonnifyFeignConfig {

  @Bean
  public RequestInterceptor monnifyAuthInterceptor(MonnifyTokenProvider tokenProvider) {
    return requestTemplate ->
        requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken());
  }
}

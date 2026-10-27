package com.lendbridge.payment.provider.impl.monnify;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class MonnifyFeignConfig {

  @Bean
  public RequestInterceptor monnifyAuthInterceptor(MonnifyTokenProvider tokenProvider) {
    return requestTemplate ->
        requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken());
  }
}

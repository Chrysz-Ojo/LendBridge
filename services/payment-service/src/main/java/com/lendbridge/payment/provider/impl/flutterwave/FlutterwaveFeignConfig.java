package com.lendbridge.payment.provider.impl.flutterwave;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class FlutterwaveFeignConfig {

  @Bean
  public RequestInterceptor flutterwaveAuthInterceptor(@Value("${flutterwave.secret-key}") String secretKey) {
    return requestTemplate -> requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey);
  }
}

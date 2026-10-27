package com.lendbridge.payment.provider.impl.flutterwave;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/** Deliberately NOT {@code @Configuration} — see {@code PaystackFeignConfig}'s javadoc for why:
 * only referenced via {@code @FeignClient(configuration = FlutterwaveFeignConfig.class)}, never
 * component-scanned as a global interceptor. */
public class FlutterwaveFeignConfig {

  @Bean
  public RequestInterceptor flutterwaveAuthInterceptor(@Value("${flutterwave.secret-key}") String secretKey) {
    return requestTemplate -> requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey);
  }
}

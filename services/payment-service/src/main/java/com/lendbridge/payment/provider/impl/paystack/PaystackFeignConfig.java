package com.lendbridge.payment.provider.impl.paystack;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/** Paystack's own secret key (test or live, same base URL either way — the key prefix alone
 * determines the mode), never the end-user's JWT — unlike {@code WalletFeignConfig}/
 * {@code AuthFeignConfig}, this Feign client talks to an external vendor, not another
 * LendBridge service. */
@Configuration
public class PaystackFeignConfig {

  @Bean
  public RequestInterceptor paystackAuthInterceptor(@Value("${paystack.secret-key}") String secretKey) {
    return requestTemplate -> requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey);
  }
}

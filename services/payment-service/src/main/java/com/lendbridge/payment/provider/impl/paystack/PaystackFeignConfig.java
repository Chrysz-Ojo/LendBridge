package com.lendbridge.payment.provider.impl.paystack;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/** Paystack's own secret key (test or live, same base URL either way — the key prefix alone
 * determines the mode), never the end-user's JWT — unlike {@code WalletFeignConfig}/
 * {@code AuthFeignConfig}, this Feign client talks to an external vendor, not another
 * LendBridge service.
 *
 * <p>Deliberately NOT {@code @Configuration} — only referenced via {@code @FeignClient(configuration
 * = PaystackFeignConfig.class)}. Spring Cloud OpenFeign treats any {@code RequestInterceptor} bean
 * visible from the main application context as global, applied to every Feign client; if this were
 * a regular {@code @Configuration} bean, the broad {@code @ComponentScan(basePackages =
 * "com.lendbridge")} in {@code PaymentServiceApplication} would register it in the root context and
 * this interceptor would run on every other provider's/service's Feign calls too (confirmed in CI:
 * exactly this bug once happened to Monnify's own config, whose interceptor makes a real network
 * call). A plain class with a {@code @Bean} method still works as a Feign {@code configuration}
 * class — Spring treats any class with a {@code @Bean} method as a "lite" configuration candidate
 * when Feign explicitly registers it into the client's own child context. */
public class PaystackFeignConfig {

  @Bean
  public RequestInterceptor paystackAuthInterceptor(@Value("${paystack.secret-key}") String secretKey) {
    return requestTemplate -> requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey);
  }
}

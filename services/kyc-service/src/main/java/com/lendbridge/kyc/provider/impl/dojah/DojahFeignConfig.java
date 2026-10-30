package com.lendbridge.kyc.provider.impl.dojah;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Dojah authenticates with two static headers — {@code AppId} (the app/project id) and
 * {@code Authorization} carrying the raw secret key, not a {@code Bearer}-prefixed token — modeled
 * from general knowledge of Dojah's API, not confirmed against live docs this session.
 *
 * <p>Deliberately NOT {@code @Configuration} — see {@code DocumentFeignConfig}'s javadoc for why,
 * and {@code FeignConfigurationIsolationIT} for the regression test.
 */
public class DojahFeignConfig {

  @Bean
  public RequestInterceptor dojahAuthInterceptor(
      @Value("${dojah.app-id}") String appId, @Value("${dojah.secret-key}") String secretKey) {
    return requestTemplate -> {
      requestTemplate.header("AppId", appId);
      requestTemplate.header("Authorization", secretKey);
    };
  }
}

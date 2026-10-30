package com.lendbridge.kyc.provider.impl.prembly;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Prembly/Identitypass authenticates with two static headers — {@code x-api-key} and
 * {@code app-id} — modeled from general knowledge of Prembly's API, not confirmed against live
 * docs this session.
 *
 * <p>Deliberately NOT {@code @Configuration} — see {@code DocumentFeignConfig}'s javadoc for why,
 * and {@code FeignConfigurationIsolationIT} for the regression test.
 */
public class PremblyFeignConfig {

  @Bean
  public RequestInterceptor premblyAuthInterceptor(
      @Value("${prembly.api-key}") String apiKey, @Value("${prembly.app-id}") String appId) {
    return requestTemplate -> {
      requestTemplate.header("x-api-key", apiKey);
      requestTemplate.header("app-id", appId);
    };
  }
}

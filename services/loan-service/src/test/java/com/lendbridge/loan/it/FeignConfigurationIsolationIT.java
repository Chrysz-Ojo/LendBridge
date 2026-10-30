package com.lendbridge.loan.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.loan.kyc.KycFeignConfig;
import com.lendbridge.loan.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Regression test for the exact CI failure payment-service's own {@code FeignConfigurationIsolationIT}
 * documents: {@code KycFeignConfig} must stay OUT of the main {@code @ComponentScan} — if it were a
 * global bean, Spring Cloud OpenFeign would treat its Authorization-forwarding interceptor as
 * applying to every {@code @FeignClient}, not just {@code KycClient}.
 */
class FeignConfigurationIsolationIT extends AbstractIntegrationTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void kycFeignConfig_isNotRegisteredAsAGlobalBean() {
    assertThat(applicationContext.getBeanNamesForType(KycFeignConfig.class)).isEmpty();
  }
}

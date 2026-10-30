package com.lendbridge.investment.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.investment.support.AbstractIntegrationTest;
import com.lendbridge.investment.wallet.WalletFeignConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** Regression test for the exact class of bug loan-service's/payment-service's own
 * {@code FeignConfigurationIsolationIT} guard against: {@code WalletFeignConfig} must stay OUT of
 * the main {@code @ComponentScan} — if it were a global bean, Spring Cloud OpenFeign would treat
 * its Authorization-forwarding interceptor as applying to every {@code @FeignClient}, not just
 * {@code WalletClient}. */
class FeignConfigurationIsolationIT extends AbstractIntegrationTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void walletFeignConfig_isNotRegisteredAsAGlobalBean() {
    assertThat(applicationContext.getBeanNamesForType(WalletFeignConfig.class)).isEmpty();
  }
}

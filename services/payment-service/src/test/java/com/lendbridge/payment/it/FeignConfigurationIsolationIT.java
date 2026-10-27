package com.lendbridge.payment.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.payment.authentication.AuthFeignConfig;
import com.lendbridge.payment.provider.impl.flutterwave.FlutterwaveFeignConfig;
import com.lendbridge.payment.provider.impl.monnify.MonnifyFeignConfig;
import com.lendbridge.payment.provider.impl.paystack.PaystackFeignConfig;
import com.lendbridge.payment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Regression test for a real CI failure: each {@code *FeignConfig} class must stay OUT of the
 * main {@code @ComponentScan} — Spring Cloud OpenFeign treats any {@code RequestInterceptor} bean
 * visible from the root application context as global, applied to every {@code @FeignClient}, not
 * just the one it was written for. With these classes annotated {@code @Configuration}, Monnify's
 * interceptor (which makes a real network call to refresh its access token) was being applied to
 * {@code WalletClient}/{@code AuthClient} too, breaking every top-up/withdrawal request with a
 * confusing "WALLET_SERVICE_UNAVAILABLE" error whose actual cause was a failed call to Monnify's
 * sandbox. See each config class's own javadoc for the fix (not {@code @Configuration}, only
 * referenced via each {@code @FeignClient}'s own {@code configuration} attribute).
 */
class FeignConfigurationIsolationIT extends AbstractIntegrationTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void feignConfigClasses_areNotRegisteredAsGlobalBeans() {
    assertThat(applicationContext.getBeanNamesForType(PaystackFeignConfig.class)).isEmpty();
    assertThat(applicationContext.getBeanNamesForType(FlutterwaveFeignConfig.class)).isEmpty();
    assertThat(applicationContext.getBeanNamesForType(MonnifyFeignConfig.class)).isEmpty();
    assertThat(applicationContext.getBeanNamesForType(com.lendbridge.payment.wallet.WalletFeignConfig.class))
        .isEmpty();
    assertThat(applicationContext.getBeanNamesForType(AuthFeignConfig.class)).isEmpty();
  }
}

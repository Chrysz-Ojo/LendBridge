package com.lendbridge.kyc.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.kyc.document.DocumentFeignConfig;
import com.lendbridge.kyc.provider.impl.dojah.DojahFeignConfig;
import com.lendbridge.kyc.provider.impl.prembly.PremblyFeignConfig;
import com.lendbridge.kyc.provider.impl.smileidentity.SmileIdentityFeignConfig;
import com.lendbridge.kyc.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Regression test for the exact CI failure payment-service's own {@code FeignConfigurationIsolationIT}
 * documents: each {@code *FeignConfig} class must stay OUT of the main {@code @ComponentScan} —
 * Spring Cloud OpenFeign treats any {@code RequestInterceptor} bean visible from the root
 * application context as global, applied to every {@code @FeignClient}, not just the one it was
 * written for. If {@code DocumentFeignConfig} were a global bean, its Authorization-forwarding
 * interceptor would also run on the three external-provider Feign clients, forwarding the
 * end-user's own bearer token to Smile ID/Dojah/Prembly instead of each vendor's own API key.
 */
class FeignConfigurationIsolationIT extends AbstractIntegrationTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void feignConfigClasses_areNotRegisteredAsGlobalBeans() {
    assertThat(applicationContext.getBeanNamesForType(DocumentFeignConfig.class)).isEmpty();
    assertThat(applicationContext.getBeanNamesForType(SmileIdentityFeignConfig.class)).isEmpty();
    assertThat(applicationContext.getBeanNamesForType(DojahFeignConfig.class)).isEmpty();
    assertThat(applicationContext.getBeanNamesForType(PremblyFeignConfig.class)).isEmpty();
  }
}

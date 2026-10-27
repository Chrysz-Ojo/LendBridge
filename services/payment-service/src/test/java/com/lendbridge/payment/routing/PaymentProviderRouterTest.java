package com.lendbridge.payment.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.provider.ChargeRequest;
import com.lendbridge.payment.provider.PaymentProvider;
import com.lendbridge.payment.provider.ProviderChargeResult;
import com.lendbridge.payment.provider.ProviderOperationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentProviderRouterTest {

  @Test
  void initiateTopUpCharge_usesPrimaryProvider() {
    PaymentProvider primary = fakeProvider(ProviderName.PAYSTACK);
    PaymentProvider secondary = fakeProvider(ProviderName.FLUTTERWAVE);
    when(primary.initiateCharge(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            ProviderChargeResult.builder()
                .providerReference("ref-1")
                .status(ProviderOperationStatus.SUCCESSFUL)
                .build());

    PaymentProviderRouter router =
        new PaymentProviderRouter(List.of(primary, secondary), "paystack", "flutterwave");

    RoutedChargeResult result = router.initiateTopUpCharge(new ChargeRequest("ref", 1000, "NGN", "a@b.com", "idem-1"));

    assertThat(result.getProvider()).isEqualTo(ProviderName.PAYSTACK);
    assertThat(result.getResult().getProviderReference()).isEqualTo("ref-1");
  }

  @Test
  void primaryProviderName_returnsConfiguredPrimary() {
    PaymentProvider primary = fakeProvider(ProviderName.MONNIFY);
    PaymentProvider secondary = fakeProvider(ProviderName.FAKE);

    PaymentProviderRouter router =
        new PaymentProviderRouter(List.of(primary, secondary), "monnify", "fake");

    assertThat(router.primaryProviderName()).isEqualTo(ProviderName.MONNIFY);
  }

  @Test
  void provider_throwsForUnregisteredProviderName() {
    PaymentProvider onlyProvider = fakeProvider(ProviderName.FAKE);
    PaymentProviderRouter router =
        new PaymentProviderRouter(List.of(onlyProvider), "fake", "fake");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> router.provider(ProviderName.PAYSTACK))
        .isInstanceOf(IllegalStateException.class);
  }

  private PaymentProvider fakeProvider(ProviderName name) {
    PaymentProvider provider = mock(PaymentProvider.class);
    when(provider.name()).thenReturn(name);
    return provider;
  }
}

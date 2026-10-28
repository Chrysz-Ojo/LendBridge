package com.lendbridge.kyc.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.provider.BvnCheckRequest;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import com.lendbridge.kyc.provider.VerificationProvider;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationProviderRouterTest {

  @Test
  void checkBvn_usesPrimaryProvider_whenPrimarySucceeds() {
    VerificationProvider primary = fakeProvider(ProviderName.SMILE_IDENTITY);
    VerificationProvider secondary = fakeProvider(ProviderName.DOJAH);
    when(primary.checkBvn(org.mockito.ArgumentMatchers.any()))
        .thenReturn(ProviderCheckResult.builder().outcome(CheckOutcome.PASSED).build());

    VerificationProviderRouter router =
        new VerificationProviderRouter(List.of(primary, secondary), "smile_identity", "dojah");

    RoutedCheckResult result = router.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));

    assertThat(result.getProvider()).isEqualTo(ProviderName.SMILE_IDENTITY);
    assertThat(result.getResult().getOutcome()).isEqualTo(CheckOutcome.PASSED);
  }

  // Resilience4j's @CircuitBreaker/@Retry annotations only trigger a fallback through a Spring AOP
  // proxy, which a plain `new VerificationProviderRouter(...)` unit test never has — the same
  // reason payment-service's own PaymentProviderRouterTest never exercises its fallback through the
  // annotated entry point either. These two tests call the (package-private)
  // *Fallback method directly, which is exactly what Resilience4j would invoke at runtime, to
  // cover the secondary-routing and both-down business logic deterministically.
  @Test
  void checkBvnFallback_routesToSecondary_whenSecondarySucceeds() {
    VerificationProvider primary = fakeProvider(ProviderName.SMILE_IDENTITY);
    VerificationProvider secondary = fakeProvider(ProviderName.DOJAH);
    when(secondary.checkBvn(org.mockito.ArgumentMatchers.any()))
        .thenReturn(ProviderCheckResult.builder().outcome(CheckOutcome.PASSED).build());

    VerificationProviderRouter router =
        new VerificationProviderRouter(List.of(primary, secondary), "smile_identity", "dojah");

    RoutedCheckResult result =
        router.checkBvnFallback(
            new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)), new RuntimeException("primary down"));

    assertThat(result.getProvider()).isEqualTo(ProviderName.DOJAH);
    assertThat(result.getResult().getOutcome()).isEqualTo(CheckOutcome.PASSED);
  }

  @Test
  void checkBvnFallback_throwsDependencyUnavailable_whenSecondaryAlsoFails() {
    VerificationProvider primary = fakeProvider(ProviderName.SMILE_IDENTITY);
    VerificationProvider secondary = fakeProvider(ProviderName.DOJAH);
    when(secondary.checkBvn(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("secondary down"));

    VerificationProviderRouter router =
        new VerificationProviderRouter(List.of(primary, secondary), "smile_identity", "dojah");

    assertThatThrownBy(
            () ->
                router.checkBvnFallback(
                    new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)),
                    new RuntimeException("primary down")))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  @Test
  void provider_throwsForUnregisteredProviderName() {
    VerificationProvider onlyProvider = fakeProvider(ProviderName.FAKE);
    VerificationProviderRouter router = new VerificationProviderRouter(List.of(onlyProvider), "fake", "fake");

    assertThatThrownBy(() -> router.provider(ProviderName.PREMBLY)).isInstanceOf(IllegalStateException.class);
  }

  private VerificationProvider fakeProvider(ProviderName name) {
    VerificationProvider provider = mock(VerificationProvider.class);
    when(provider.name()).thenReturn(name);
    return provider;
  }
}

package com.lendbridge.payment.routing;

import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.provider.ChargeRequest;
import com.lendbridge.payment.provider.PayoutRequest;
import com.lendbridge.payment.provider.PaymentProvider;
import com.lendbridge.payment.provider.ProviderChargeResult;
import com.lendbridge.payment.provider.ProviderPayoutResult;
import com.lendbridge.payment.provider.ProviderVirtualAccountResult;
import com.lendbridge.payment.provider.VirtualAccountRequest;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The PRD's own named risk mitigation for "Payment or KYC provider outage": adapter pattern with
 * a secondary provider, circuit breakers, queued retries — scoped to top-up charge initiation
 * only. Virtual-account provisioning and payouts are deliberately NOT routed: a dedicated virtual
 * account is permanently tied to whichever provider created it (no transparent mid-life swap),
 * and payouts always target the primary directly, failing loud on circuit-open rather than
 * silently moving money leaving the platform through a different, un-reviewed provider.
 */
@Service
public class PaymentProviderRouter {

  private static final Logger log = LoggerFactory.getLogger(PaymentProviderRouter.class);

  private final Map<ProviderName, PaymentProvider> providersByName;
  private final ProviderName primary;
  private final ProviderName secondary;

  public PaymentProviderRouter(
      List<PaymentProvider> providers,
      @Value("${payment.provider.primary}") String primary,
      @Value("${payment.provider.secondary}") String secondary) {
    this.providersByName = providers.stream().collect(java.util.stream.Collectors.toMap(PaymentProvider::name, Function.identity()));
    this.primary = ProviderName.valueOf(primary.toUpperCase());
    this.secondary = ProviderName.valueOf(secondary.toUpperCase());
  }

  @CircuitBreaker(name = "paymentProviderPrimary", fallbackMethod = "initiateTopUpChargeFallback")
  @Retry(name = "paymentProviderPrimary")
  public RoutedChargeResult initiateTopUpCharge(ChargeRequest request) {
    return new RoutedChargeResult(primary, provider(primary).initiateCharge(request));
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as initiateTopUpCharge's fallback
  private RoutedChargeResult initiateTopUpChargeFallback(ChargeRequest request, Throwable cause) {
    log.warn("Primary provider {} failed for top-up charge, falling back to secondary {}", primary, secondary, cause);
    return new RoutedChargeResult(secondary, provider(secondary).initiateCharge(request));
  }

  public ProviderVirtualAccountResult provisionVirtualAccount(VirtualAccountRequest request) {
    try {
      return provider(primary).provisionVirtualAccount(request);
    } catch (Exception e) {
      throw new DependencyUnavailableException(
          "PAYMENT_PROVIDER_UNAVAILABLE", "Unable to provision a virtual account: " + e.getMessage());
    }
  }

  @CircuitBreaker(name = "paymentProviderPayout", fallbackMethod = "initiatePayoutFallback")
  public ProviderPayoutResult initiatePayout(PayoutRequest request) {
    return provider(primary).initiatePayout(request);
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as initiatePayout's fallback
  private ProviderPayoutResult initiatePayoutFallback(PayoutRequest request, Throwable cause) {
    throw new DependencyUnavailableException(
        "PAYMENT_PROVIDER_UNAVAILABLE",
        "Primary payment provider is unavailable for payouts: " + cause.getMessage());
  }

  public PaymentProvider provider(ProviderName name) {
    PaymentProvider provider = providersByName.get(name);
    if (provider == null) {
      throw new IllegalStateException("No PaymentProvider registered for " + name);
    }
    return provider;
  }

  /** Virtual-account provisioning and payouts always target this directly — never routed, see
   * the class-level javadoc. */
  public ProviderName primaryProviderName() {
    return primary;
  }
}

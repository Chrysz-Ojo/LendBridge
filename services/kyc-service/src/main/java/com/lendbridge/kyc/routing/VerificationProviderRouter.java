package com.lendbridge.kyc.routing;

import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.provider.BvnCheckRequest;
import com.lendbridge.kyc.provider.LivenessCheckRequest;
import com.lendbridge.kyc.provider.NinCheckRequest;
import com.lendbridge.kyc.provider.VerificationProvider;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Primary/secondary routing for all three check verbs, mirroring payment-service's
 * {@code PaymentProviderRouter} exactly (see the approved plan's design decisions): a
 * {@code List<VerificationProvider>} is injected, primary/secondary resolved from
 * {@code kyc.provider.primary}/{@code secondary}, each check wrapped in
 * {@code @CircuitBreaker}/{@code @Retry} with a fallback to the secondary provider. If the
 * secondary also fails, that is a transient dependency outage (outcome routing rule 4) — never
 * recorded as a {@code FAILED} check — so the fallback wraps any secondary failure in
 * {@link DependencyUnavailableException} rather than letting a raw provider SDK exception escape.
 * The third configured provider stays reachable via direct {@link #provider(ProviderName)} lookup
 * but isn't in the auto-failover chain, matching payment-service's one 3-adapter precedent rather
 * than building a fancier N-way router.
 */
@Service
public class VerificationProviderRouter {

  private static final Logger log = LoggerFactory.getLogger(VerificationProviderRouter.class);

  private final Map<ProviderName, VerificationProvider> providersByName;
  private final ProviderName primary;
  private final ProviderName secondary;

  public VerificationProviderRouter(
      List<VerificationProvider> providers,
      @Value("${kyc.provider.primary}") String primary,
      @Value("${kyc.provider.secondary}") String secondary) {
    this.providersByName =
        providers.stream().collect(Collectors.toMap(VerificationProvider::name, Function.identity()));
    this.primary = ProviderName.valueOf(primary.toUpperCase());
    this.secondary = ProviderName.valueOf(secondary.toUpperCase());
  }


  @CircuitBreaker(name = "kycProviderPrimary", fallbackMethod = "checkBvnFallback")
  @Retry(name = "kycProviderPrimary")
  public RoutedCheckResult checkBvn(BvnCheckRequest request) {
    return new RoutedCheckResult(primary, provider(primary).checkBvn(request));
  }

  RoutedCheckResult checkBvnFallback(BvnCheckRequest request, Throwable cause) {
    log.warn("Primary KYC provider {} failed for BVN check, falling back to secondary {}", primary, secondary, cause);
    try {
      return new RoutedCheckResult(secondary, provider(secondary).checkBvn(request));
    } catch (Exception e) {
      throw unavailable("BVN", e);
    }
  }

  @CircuitBreaker(name = "kycProviderPrimary", fallbackMethod = "checkNinFallback")
  @Retry(name = "kycProviderPrimary")
  public RoutedCheckResult checkNin(NinCheckRequest request) {
    return new RoutedCheckResult(primary, provider(primary).checkNin(request));
  }

  // See checkBvnFallback's comment above for why this is package-private, not private.
  RoutedCheckResult checkNinFallback(NinCheckRequest request, Throwable cause) {
    log.warn("Primary KYC provider {} failed for NIN check, falling back to secondary {}", primary, secondary, cause);
    try {
      return new RoutedCheckResult(secondary, provider(secondary).checkNin(request));
    } catch (Exception e) {
      throw unavailable("NIN", e);
    }
  }

  @CircuitBreaker(name = "kycProviderPrimary", fallbackMethod = "checkLivenessFallback")
  @Retry(name = "kycProviderPrimary")
  public RoutedCheckResult checkLiveness(LivenessCheckRequest request) {
    return new RoutedCheckResult(primary, provider(primary).checkLiveness(request));
  }

  // See checkBvnFallback's comment above for why this is package-private, not private.
  RoutedCheckResult checkLivenessFallback(LivenessCheckRequest request, Throwable cause) {
    log.warn(
        "Primary KYC provider {} failed for liveness check, falling back to secondary {}", primary, secondary, cause);
    try {
      return new RoutedCheckResult(secondary, provider(secondary).checkLiveness(request));
    } catch (Exception e) {
      throw unavailable("liveness", e);
    }
  }

  public VerificationProvider provider(ProviderName name) {
    VerificationProvider provider = providersByName.get(name);
    if (provider == null) {
      throw new IllegalStateException("No VerificationProvider registered for " + name);
    }
    return provider;
  }

  private DependencyUnavailableException unavailable(String checkName, Exception cause) {
    return new DependencyUnavailableException(
        "KYC_PROVIDER_UNAVAILABLE",
        "Both primary and secondary KYC providers are unavailable for the " + checkName + " check: " + cause.getMessage());
  }
}

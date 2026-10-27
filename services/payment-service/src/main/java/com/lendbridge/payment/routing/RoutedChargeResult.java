package com.lendbridge.payment.routing;

import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.provider.ProviderChargeResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Pairs a charge result with the provider that actually serviced it — needed because a
 * circuit-breaker fallback may have routed the charge to the secondary provider instead of the
 * configured primary, and the caller must persist the correct provider so a later webhook on that
 * provider's path can be matched back to this charge by {@code provider + providerReference}. */
@Getter
@AllArgsConstructor
public class RoutedChargeResult {
  private final ProviderName provider;
  private final ProviderChargeResult result;
}

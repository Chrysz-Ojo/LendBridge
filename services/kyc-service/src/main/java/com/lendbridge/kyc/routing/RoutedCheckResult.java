package com.lendbridge.kyc.routing;

import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Pairs a check result with the provider that actually serviced it — needed because a
 * circuit-breaker fallback may have routed the check to the secondary provider instead of the
 * configured primary, and the caller persists the correct provider on the result row (mirrors
 * payment-service's {@code RoutedChargeResult} exactly). */
@Getter
@AllArgsConstructor
public class RoutedCheckResult {
  private final ProviderName provider;
  private final ProviderCheckResult result;
}

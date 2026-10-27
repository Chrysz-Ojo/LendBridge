package com.lendbridge.payment.provider;

import com.lendbridge.payment.model.ProviderName;
import java.time.Instant;
import java.util.List;

/** Deliberately NOT part of {@link PaymentProvider} — real providers don't expose settlement
 * data uniformly via API; in reality it's an out-of-band file or dashboard export. Only the fake
 * implements this, since there's no real settlement file to test reconciliation against anyway. */
public interface SettlementReportSource {

  List<SettlementRecord> generateSettlementReport(ProviderName provider, Instant from, Instant to);
}

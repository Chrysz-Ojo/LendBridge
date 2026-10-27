package com.lendbridge.ledger;

import java.util.UUID;

/** Fixed, well-known ids for migration-seeded clearing accounts — see
 * {@code db/changelog/changes/006-seed-payment-clearing-account.yaml}. Must match that
 * changeset's literal UUID exactly. */
public final class ClearingAccounts {

  public static final UUID PAYMENT_CLEARING_NGN = UUID.fromString("a30150c7-0cc2-4b48-a1c0-c8a8abd8e831");

  private ClearingAccounts() {}
}

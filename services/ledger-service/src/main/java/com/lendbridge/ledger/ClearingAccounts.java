package com.lendbridge.ledger;

import java.util.UUID;

/** Fixed, well-known ids for migration-seeded clearing accounts — see
 * {@code db/changelog/changes/006-seed-payment-clearing-account.yaml} and
 * {@code 007-seed-loan-accounts.yaml}. Must match those changesets' literal UUIDs exactly. */
public final class ClearingAccounts {

  public static final UUID PAYMENT_CLEARING_NGN = UUID.fromString("a30150c7-0cc2-4b48-a1c0-c8a8abd8e831");

  /** The funding saga's intermediate hop (PRD: "lender wallets -> loan escrow account -> borrower
   * wallet") — one global account, not per-loan, same reasoning as the payment clearing account:
   * both legs of a disbursement post inside one local transaction, so there's nothing a per-loan
   * account would buy that a shared well-known one doesn't. */
  public static final UUID LOAN_ESCROW_NGN = UUID.fromString("b41261d8-1dd3-5c59-b2d1-d9b9bce9f942");

  /** Where the origination fee carved out of every disbursement lands — a CREDIT-normal revenue
   * account, same idiom as the two clearing accounts above. */
  public static final UUID PLATFORM_FEE_REVENUE_NGN = UUID.fromString("c52372e9-2ee4-6d6a-c3e2-eacacdfa0a53");

  private ClearingAccounts() {}
}

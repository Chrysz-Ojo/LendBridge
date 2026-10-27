package com.lendbridge.wallet.service;

import com.lendbridge.wallet.dto.WalletResponse;
import com.lendbridge.wallet.model.Wallet;
import java.util.Optional;
import java.util.UUID;

public interface WalletService {

  WalletResponse getWalletSummary(UUID userId);

  /**
   * Ensures a {@link Wallet} row exists and its backing ledger account is provisioned, lazily
   * opening one with Ledger on first call if needed. Takes a row lock, so this must never be called
   * from a read-only transaction (Postgres rejects {@code SELECT ... FOR UPDATE} under one) — use
   * {@link #findWallet(UUID)} instead for read-only lookups that shouldn't trigger provisioning as
   * a side effect.
   */
  Wallet getOrCreateWallet(UUID userId);

  /**
   * Plain, non-locking lookup — safe from read-only transactions. Returns empty if the wallet
   * hasn't been provisioned yet; callers that don't need provisioning (e.g. listing holds) should
   * treat that as "nothing to show yet," not an error.
   */
  Optional<Wallet> findWallet(UUID userId);
}

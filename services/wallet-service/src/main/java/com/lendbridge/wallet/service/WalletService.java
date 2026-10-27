package com.lendbridge.wallet.service;

import com.lendbridge.wallet.dto.WalletResponse;
import com.lendbridge.wallet.model.Wallet;
import java.util.UUID;

public interface WalletService {

  WalletResponse getWalletSummary(UUID userId);

  /** Ensures a {@link Wallet} row exists and its backing ledger account is provisioned,
   * lazily opening one with Ledger on first call if needed. Used by every other wallet-service
   * operation that needs a wallet (holds, bank accounts, virtual accounts), not just the
   * GET /wallets/me endpoint. */
  Wallet getOrCreateWallet(UUID userId);
}

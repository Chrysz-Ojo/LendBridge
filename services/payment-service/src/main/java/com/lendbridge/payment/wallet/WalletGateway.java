package com.lendbridge.payment.wallet;

import com.lendbridge.starter.exception.DependencyUnavailableException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The only thing the rest of payment-service calls to reach wallet-service. Unlike
 * wallet-service's own {@code LedgerGateway}, none of these three calls get a cache fallback —
 * all are money-moving-decision inputs (where to credit a top-up, whether a bank account is
 * verified before a payout) that must fail closed rather than serve stale data. */
@Service
public class WalletGateway {

  private final WalletClient walletClient;

  public WalletGateway(WalletClient walletClient) {
    this.walletClient = walletClient;
  }

  public WalletSummaryResponse getWalletSummary() {
    try {
      return walletClient.getWalletSummary().getData();
    } catch (Exception e) {
      throw new DependencyUnavailableException("WALLET_SERVICE_UNAVAILABLE", "Unable to reach wallet-service: " + e.getMessage());
    }
  }

  public BankAccountPayoutDetails getBankAccountForPayout(UUID bankAccountId) {
    try {
      return walletClient.getBankAccountForPayout(bankAccountId).getData();
    } catch (Exception e) {
      throw new DependencyUnavailableException("WALLET_SERVICE_UNAVAILABLE", "Unable to reach wallet-service: " + e.getMessage());
    }
  }

  public void registerVirtualAccount(RegisterVirtualAccountCommand command) {
    try {
      walletClient.registerVirtualAccount(command);
    } catch (Exception e) {
      throw new DependencyUnavailableException("WALLET_SERVICE_UNAVAILABLE", "Unable to reach wallet-service: " + e.getMessage());
    }
  }
}

package com.lendbridge.investment.wallet;

import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** The only thing the rest of this service calls to reach wallet-service. Resilience4j
 * annotations live here, not on {@link WalletClient} directly — same reasoning as loan-service's
 * own {@code KycGateway}. */
@Service
public class WalletGateway {

  private static final Logger log = LoggerFactory.getLogger(WalletGateway.class);

  private final WalletClient walletClient;

  public WalletGateway(WalletClient walletClient) {
    this.walletClient = walletClient;
  }

  @CircuitBreaker(name = "walletService", fallbackMethod = "createHoldFallback")
  @Retry(name = "walletService")
  public UUID createHold(String idempotencyKey, long amountKobo, String reference) {
    HoldResult result = walletClient.createHold(idempotencyKey, new CreateHoldCommand(amountKobo, reference)).getData();
    return result.getId();
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as createHold's fallback
  private UUID createHoldFallback(String idempotencyKey, long amountKobo, String reference, Throwable cause) {
    log.warn("wallet-service unreachable while creating a hold", cause);
    throw new DependencyUnavailableException(
        "WALLET_SERVICE_UNAVAILABLE", "Unable to reserve funds: " + cause.getMessage());
  }

  @CircuitBreaker(name = "walletService", fallbackMethod = "releaseHoldFallback")
  @Retry(name = "walletService")
  public void releaseHold(UUID holdId) {
    walletClient.releaseHold(holdId);
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as releaseHold's fallback
  private void releaseHoldFallback(UUID holdId, Throwable cause) {
    log.warn("wallet-service unreachable while releasing a hold", cause);
    throw new DependencyUnavailableException(
        "WALLET_SERVICE_UNAVAILABLE", "Unable to release the held funds: " + cause.getMessage());
  }
}

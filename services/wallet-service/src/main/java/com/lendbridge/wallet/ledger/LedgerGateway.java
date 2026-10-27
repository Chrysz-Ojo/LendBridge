package com.lendbridge.wallet.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * The only thing the rest of wallet-service calls to reach ledger-service. Resilience4j annotations
 * live here, not on {@link LedgerClient} directly — Feign clients are Spring-generated proxies, and
 * wrapping the call in an explicit service method avoids proxy-on-proxy ambiguity.
 */
@Service
public class LedgerGateway {

  private static final Logger log = LoggerFactory.getLogger(LedgerGateway.class);
  private static final String CACHE_KEY_PREFIX = "wallet:ledger-balance:";

  private final LedgerClient ledgerClient;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration cacheTtl;

  public LedgerGateway(
      LedgerClient ledgerClient,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${wallet.ledger-balance-cache-ttl-seconds:60}") long cacheTtlSeconds) {
    this.ledgerClient = ledgerClient;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
  }

  /**
   * Runs once per wallet (lazy provisioning), not a hot path — no circuit breaker; a failure here
   * is surfaced directly rather than retried/broken-circuited.
   */
  public LedgerAccountResponse openAccount(OpenLedgerAccountRequest request) {
    try {
      return ledgerClient.openAccount(request).getData();
    } catch (Exception e) {
      throw new DependencyUnavailableException(
          "LEDGER_UNAVAILABLE", "Unable to open a ledger account: " + e.getMessage());
    }
  }

  @CircuitBreaker(name = "ledgerBalance", fallbackMethod = "getBalanceFallback")
  @Retry(name = "ledgerBalance")
  public LedgerBalanceSnapshot getBalance(UUID ledgerAccountId) {
    LedgerBalanceResponse response = ledgerClient.getBalance(ledgerAccountId).getData();
    writeCache(ledgerAccountId, response.getBalanceKobo(), response.getCurrency());
    return new LedgerBalanceSnapshot(
        response.getBalanceKobo(), response.getCurrency(), false, null);
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as getBalance's fallback
  private LedgerBalanceSnapshot getBalanceFallback(UUID ledgerAccountId, Throwable cause) {
    log.warn(
        "Ledger balance read failed for account {}, falling back to cache", ledgerAccountId, cause);
    CachedLedgerBalance cached = readCache(ledgerAccountId);
    if (cached == null) {
      throw new DependencyUnavailableException(
          "LEDGER_UNAVAILABLE", "Ledger is unavailable and no cached balance exists yet");
    }
    return new LedgerBalanceSnapshot(
        cached.getBalanceKobo(),
        cached.getCurrency(),
        true,
        Instant.ofEpochMilli(cached.getCachedAtEpochMillis()));
  }

  private void writeCache(UUID ledgerAccountId, long balanceKobo, String currency) {
    try {
      CachedLedgerBalance cached =
          new CachedLedgerBalance(balanceKobo, currency, Instant.now().toEpochMilli());
      redisTemplate
          .opsForValue()
          .set(
              CACHE_KEY_PREFIX + ledgerAccountId,
              objectMapper.writeValueAsString(cached),
              cacheTtl);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize ledger balance cache entry for {}", ledgerAccountId, e);
    }
  }

  private CachedLedgerBalance readCache(UUID ledgerAccountId) {
    String json = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + ledgerAccountId);
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, CachedLedgerBalance.class);
    } catch (JsonProcessingException e) {
      log.warn("Failed to deserialize ledger balance cache entry for {}", ledgerAccountId, e);
      return null;
    }
  }
}

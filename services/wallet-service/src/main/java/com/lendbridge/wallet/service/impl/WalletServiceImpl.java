package com.lendbridge.wallet.service.impl;

import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.dto.WalletResponse;
import com.lendbridge.wallet.ledger.LedgerAccountResponse;
import com.lendbridge.wallet.ledger.LedgerBalanceSnapshot;
import com.lendbridge.wallet.ledger.LedgerGateway;
import com.lendbridge.wallet.ledger.OpenLedgerAccountRequest;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletCreatedPayload;
import com.lendbridge.wallet.repository.HoldRepository;
import com.lendbridge.wallet.repository.WalletRepository;
import com.lendbridge.wallet.service.WalletService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

  private static final String CURRENCY = "NGN";
  private static final String LEDGER_ACCOUNT_TYPE = "WALLET";
  private static final String LEDGER_NORMAL_BALANCE = "CREDIT";

  private final WalletRepository walletRepository;
  private final HoldRepository holdRepository;
  private final LedgerGateway ledgerGateway;
  private final OutboxEventWriter outboxEventWriter;

  @Override
  @Transactional
  public WalletResponse getWalletSummary(UUID userId) {
    Wallet wallet = getOrCreateWallet(userId);
    LedgerBalanceSnapshot snapshot = ledgerGateway.getBalance(wallet.getLedgerAccountId());
    long heldTotal = holdRepository.sumActiveHoldsByWalletId(wallet.getId());
    return WalletResponse.builder()
        .id(wallet.getId())
        .ledgerAccountId(wallet.getLedgerAccountId())
        .currency(wallet.getCurrency())
        .ledgerBalanceKobo(snapshot.getBalanceKobo())
        .heldKobo(heldTotal)
        .availableKobo(snapshot.getBalanceKobo() - heldTotal)
        .stale(snapshot.isStale())
        .staleAsOf(snapshot.getStaleAsOf())
        .build();
  }

  @Override
  @Transactional
  public Wallet getOrCreateWallet(UUID userId) {
    Wallet wallet =
        walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> createWalletRecord(userId));
    if (wallet.getLedgerAccountId() == null) {
      LedgerAccountResponse ledgerAccount =
          ledgerGateway.openAccount(
              new OpenLedgerAccountRequest(
                  LEDGER_ACCOUNT_TYPE,
                  wallet.getCurrency(),
                  LEDGER_NORMAL_BALANCE,
                  userId.toString()));
      wallet.setLedgerAccountId(ledgerAccount.getId());
      wallet.setUpdatedAt(Instant.now());
      walletRepository.save(wallet);
    }
    return wallet;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Wallet> findWallet(UUID userId) {
    return walletRepository.findByUserId(userId);
  }

  private Wallet createWalletRecord(UUID userId) {
    Instant now = Instant.now();
    Wallet wallet =
        Wallet.builder()
            .id(UuidV7.generate())
            .userId(userId)
            .currency(CURRENCY)
            .createdAt(now)
            .updatedAt(now)
            .build();
    try {
      walletRepository.save(wallet);
    } catch (DataIntegrityViolationException e) {
      // Lost a race against the user.registered consumer (or a concurrent request) creating the
      // same wallet first.
      return walletRepository.findByUserIdForUpdate(userId).orElseThrow(() -> e);
    }
    outboxEventWriter.write(
        "Wallet",
        wallet.getId().toString(),
        KafkaTopics.WALLET_CREATED,
        new WalletCreatedPayload(
            wallet.getId().toString(), userId.toString(), CURRENCY, now.toEpochMilli()));
    return wallet;
  }
}

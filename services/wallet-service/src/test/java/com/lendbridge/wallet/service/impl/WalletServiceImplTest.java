package com.lendbridge.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.dto.WalletResponse;
import com.lendbridge.wallet.ledger.LedgerAccountResponse;
import com.lendbridge.wallet.ledger.LedgerBalanceSnapshot;
import com.lendbridge.wallet.ledger.LedgerGateway;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.repository.HoldRepository;
import com.lendbridge.wallet.repository.WalletRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

  @Mock private WalletRepository walletRepository;
  @Mock private HoldRepository holdRepository;
  @Mock private LedgerGateway ledgerGateway;
  @Mock private OutboxEventWriter outboxEventWriter;

  private WalletServiceImpl service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service =
        new WalletServiceImpl(walletRepository, holdRepository, ledgerGateway, outboxEventWriter);
    userId = UUID.randomUUID();
  }

  @Test
  void getOrCreateWallet_opensLedgerAccount_whenNotYetProvisioned() {
    Wallet wallet = walletWithoutLedgerAccount();
    when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
    UUID ledgerAccountId = UUID.randomUUID();
    when(ledgerGateway.openAccount(any()))
        .thenReturn(
            new LedgerAccountResponse(
                ledgerAccountId, "WALLET", "NGN", "CREDIT", userId.toString()));

    Wallet result = service.getOrCreateWallet(userId);

    assertThat(result.getLedgerAccountId()).isEqualTo(ledgerAccountId);
    verify(walletRepository).save(wallet);
  }

  @Test
  void getOrCreateWallet_skipsLedgerCall_whenAlreadyProvisioned() {
    Wallet wallet = walletWithoutLedgerAccount();
    wallet.setLedgerAccountId(UUID.randomUUID());
    when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

    Wallet result = service.getOrCreateWallet(userId);

    assertThat(result.getLedgerAccountId()).isEqualTo(wallet.getLedgerAccountId());
    verify(ledgerGateway, never()).openAccount(any());
    verify(walletRepository, never()).save(any());
  }

  @Test
  void getOrCreateWallet_createsWalletRecord_whenNoneExists() {
    when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());
    UUID ledgerAccountId = UUID.randomUUID();
    when(ledgerGateway.openAccount(any()))
        .thenReturn(
            new LedgerAccountResponse(
                ledgerAccountId, "WALLET", "NGN", "CREDIT", userId.toString()));

    Wallet result = service.getOrCreateWallet(userId);

    assertThat(result.getUserId()).isEqualTo(userId);
    assertThat(result.getCurrency()).isEqualTo("NGN");
    assertThat(result.getLedgerAccountId()).isEqualTo(ledgerAccountId);
    verify(outboxEventWriter, times(1)).write(any(), any(), any(), any());
  }

  @Test
  void getWalletSummary_combinesCachedLedgerBalanceWithLiveHeldTotal() {
    Wallet wallet = walletWithoutLedgerAccount();
    wallet.setLedgerAccountId(UUID.randomUUID());
    when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
    when(ledgerGateway.getBalance(wallet.getLedgerAccountId()))
        .thenReturn(new LedgerBalanceSnapshot(10_000L, "NGN", false, null));
    when(holdRepository.sumActiveHoldsByWalletId(wallet.getId())).thenReturn(3_000L);

    WalletResponse response = service.getWalletSummary(userId);

    assertThat(response.getLedgerAccountId()).isEqualTo(wallet.getLedgerAccountId());
    assertThat(response.getLedgerBalanceKobo()).isEqualTo(10_000L);
    assertThat(response.getHeldKobo()).isEqualTo(3_000L);
    assertThat(response.getAvailableKobo()).isEqualTo(7_000L);
    assertThat(response.isStale()).isFalse();
  }

  private Wallet walletWithoutLedgerAccount() {
    Instant now = Instant.now();
    return Wallet.builder()
        .id(UUID.randomUUID())
        .userId(userId)
        .currency("NGN")
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}

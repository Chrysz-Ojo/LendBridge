package com.lendbridge.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.dto.CreateHoldRequest;
import com.lendbridge.wallet.dto.HoldResponse;
import com.lendbridge.wallet.ledger.LedgerBalanceSnapshot;
import com.lendbridge.wallet.ledger.LedgerGateway;
import com.lendbridge.wallet.mapper.HoldMapper;
import com.lendbridge.wallet.model.Hold;
import com.lendbridge.wallet.model.HoldStatus;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.repository.HoldRepository;
import com.lendbridge.wallet.service.WalletService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldServiceImplTest {

  @Mock private HoldRepository holdRepository;
  @Mock private WalletService walletService;
  @Mock private LedgerGateway ledgerGateway;
  @Mock private OutboxEventWriter outboxEventWriter;
  private HoldMapper holdMapper;

  private HoldServiceImpl service;
  private UUID userId;
  private Wallet wallet;

  @BeforeEach
  void setUp() {
    holdMapper = Mappers.getMapper(HoldMapper.class);
    service =
        new HoldServiceImpl(
            holdRepository, walletService, ledgerGateway, outboxEventWriter, holdMapper);
    userId = UUID.randomUUID();
    wallet =
        Wallet.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .currency("NGN")
            .ledgerAccountId(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Test
  void createHold_rejectsWhenAmountExceedsAvailableBalance() {
    when(holdRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    when(walletService.getOrCreateWallet(userId)).thenReturn(wallet);
    when(ledgerGateway.getBalance(wallet.getLedgerAccountId()))
        .thenReturn(new LedgerBalanceSnapshot(5_000L, "NGN", false, null));
    when(holdRepository.sumActiveHoldsByWalletId(wallet.getId())).thenReturn(4_000L);

    CreateHoldRequest request = new CreateHoldRequest(2_000L, "loan-1");

    assertThatThrownBy(() -> service.createHold(userId, request, "key-1"))
        .isInstanceOf(UnprocessableEntityException.class);
    verify(holdRepository, never()).save(any());
  }

  @Test
  void createHold_allowsAmountEqualToAvailableBalance() {
    when(holdRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    when(walletService.getOrCreateWallet(userId)).thenReturn(wallet);
    when(ledgerGateway.getBalance(wallet.getLedgerAccountId()))
        .thenReturn(new LedgerBalanceSnapshot(10_000L, "NGN", false, null));
    when(holdRepository.sumActiveHoldsByWalletId(wallet.getId())).thenReturn(0L);

    CreateHoldRequest request = new CreateHoldRequest(10_000L, "loan-1");

    HoldResponse response = service.createHold(userId, request, "key-1");

    assertThat(response.getAmountKobo()).isEqualTo(10_000L);
    assertThat(response.getStatus()).isEqualTo(HoldStatus.ACTIVE);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void createHold_idempotentReplay_returnsExistingWithoutSideEffects() {
    Hold existing =
        Hold.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .amountKobo(1_000L)
            .status(HoldStatus.ACTIVE)
            .idempotencyKey("key-1")
            .createdAt(Instant.now())
            .build();
    when(holdRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

    HoldResponse response =
        service.createHold(userId, new CreateHoldRequest(1_000L, "ref"), "key-1");

    assertThat(response.getId()).isEqualTo(existing.getId());
    verify(walletService, never()).getOrCreateWallet(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void releaseHold_marksActiveHoldReleased() {
    Hold hold =
        Hold.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .amountKobo(1_000L)
            .status(HoldStatus.ACTIVE)
            .idempotencyKey("key-1")
            .createdAt(Instant.now())
            .build();
    when(walletService.findWallet(userId)).thenReturn(Optional.of(wallet));
    when(holdRepository.findById(hold.getId())).thenReturn(Optional.of(hold));

    service.releaseHold(userId, hold.getId());

    assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
    assertThat(hold.getReleasedAt()).isNotNull();
    verify(holdRepository).save(hold);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void releaseHold_isNoOp_whenAlreadyReleased() {
    Hold hold =
        Hold.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .amountKobo(1_000L)
            .status(HoldStatus.RELEASED)
            .idempotencyKey("key-1")
            .createdAt(Instant.now())
            .releasedAt(Instant.now())
            .build();
    when(walletService.findWallet(userId)).thenReturn(Optional.of(wallet));
    when(holdRepository.findById(hold.getId())).thenReturn(Optional.of(hold));

    service.releaseHold(userId, hold.getId());

    verify(holdRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void listActiveHolds_returnsEmpty_whenWalletNotYetProvisioned() {
    when(walletService.findWallet(userId)).thenReturn(Optional.empty());

    assertThat(service.listActiveHolds(userId)).isEmpty();
    verify(holdRepository, never()).findAllByWalletIdAndStatusOrderByCreatedAtDesc(any(), any());
  }

  @Test
  void listActiveHolds_doesNotUseLockingLookup() {
    when(walletService.findWallet(userId)).thenReturn(Optional.of(wallet));
    when(holdRepository.findAllByWalletIdAndStatusOrderByCreatedAtDesc(
            wallet.getId(), HoldStatus.ACTIVE))
        .thenReturn(java.util.List.of());

    service.listActiveHolds(userId);

    verify(walletService, never()).getOrCreateWallet(any());
  }
}

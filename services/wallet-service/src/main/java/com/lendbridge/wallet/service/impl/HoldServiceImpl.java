package com.lendbridge.wallet.service.impl;

import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.dto.CreateHoldRequest;
import com.lendbridge.wallet.dto.HoldResponse;
import com.lendbridge.wallet.ledger.LedgerBalanceSnapshot;
import com.lendbridge.wallet.ledger.LedgerGateway;
import com.lendbridge.wallet.mapper.HoldMapper;
import com.lendbridge.wallet.model.Hold;
import com.lendbridge.wallet.model.HoldStatus;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletHoldCreatedPayload;
import com.lendbridge.wallet.outbox.WalletHoldReleasedPayload;
import com.lendbridge.wallet.repository.HoldRepository;
import com.lendbridge.wallet.service.HoldService;
import com.lendbridge.wallet.service.WalletService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HoldServiceImpl implements HoldService {

  private final HoldRepository holdRepository;
  private final WalletService walletService;
  private final LedgerGateway ledgerGateway;
  private final OutboxEventWriter outboxEventWriter;
  private final HoldMapper holdMapper;
  private final IdempotencyOperations idempotencyOperations;

  @Override
  @Transactional
  public HoldResponse createHold(UUID userId, CreateHoldRequest request, String idempotencyKey) {
    return idempotencyOperations.runIdempotently(
        () -> holdRepository.findByIdempotencyKey(idempotencyKey).map(hold -> toResponseWithLedgerAccount(userId, hold)),
        () -> createHoldRecord(userId, request, idempotencyKey));
  }

  /** A retried idempotent request needs {@code walletLedgerAccountId} on its replayed response
   * too, same as the first attempt — {@code findWallet}, not {@code getOrCreateWallet}: the wallet
   * already exists by definition if a hold against it does. */
  private HoldResponse toResponseWithLedgerAccount(UUID userId, Hold hold) {
    HoldResponse response = holdMapper.toResponse(hold);
    walletService.findWallet(userId).ifPresent(wallet -> response.setWalletLedgerAccountId(wallet.getLedgerAccountId()));
    return response;
  }

  private HoldResponse createHoldRecord(UUID userId, CreateHoldRequest request, String idempotencyKey) {
    Wallet wallet = walletService.getOrCreateWallet(userId);
    LedgerBalanceSnapshot snapshot = ledgerGateway.getBalance(wallet.getLedgerAccountId());
    long heldTotal = holdRepository.sumActiveHoldsByWalletId(wallet.getId());
    long available = snapshot.getBalanceKobo() - heldTotal;
    if (request.getAmountKobo() > available) {
      throw new UnprocessableEntityException(
          "INSUFFICIENT_FUNDS", "Hold amount exceeds available wallet balance");
    }

    Instant now = Instant.now();
    Hold hold =
        Hold.builder()
            .id(UuidV7.generate())
            .walletId(wallet.getId())
            .amountKobo(request.getAmountKobo())
            .reference(request.getReference())
            .status(HoldStatus.ACTIVE)
            .idempotencyKey(idempotencyKey)
            .createdAt(now)
            .build();

    holdRepository.save(hold);

    outboxEventWriter.write(
        "Hold",
        hold.getId().toString(),
        KafkaTopics.WALLET_HOLD_CREATED,
        new WalletHoldCreatedPayload(
            hold.getId().toString(),
            wallet.getId().toString(),
            userId.toString(),
            hold.getAmountKobo(),
            hold.getReference(),
            now.toEpochMilli()));

    HoldResponse response = holdMapper.toResponse(hold);
    response.setWalletLedgerAccountId(wallet.getLedgerAccountId());
    return response;
  }

  @Override
  @Transactional
  public void releaseHold(UUID userId, UUID holdId) {
    // A hold can't exist without its wallet already having been provisioned, so a plain lookup
    // (no lock, no side effect) is enough here — releasing isn't a provisioning trigger.
    Wallet wallet =
        walletService
            .findWallet(userId)
            .orElseThrow(() -> new NotFoundException("HOLD_NOT_FOUND", "Hold not found"));
    Hold hold =
        holdRepository
            .findById(holdId)
            .filter(h -> h.getWalletId().equals(wallet.getId()))
            .orElseThrow(() -> new NotFoundException("HOLD_NOT_FOUND", "Hold not found"));

    if (hold.getStatus() == HoldStatus.RELEASED) {
      return;
    }

    Instant now = Instant.now();
    hold.setStatus(HoldStatus.RELEASED);
    hold.setReleasedAt(now);
    holdRepository.save(hold);

    outboxEventWriter.write(
        "Hold",
        hold.getId().toString(),
        KafkaTopics.WALLET_HOLD_RELEASED,
        new WalletHoldReleasedPayload(
            hold.getId().toString(),
            wallet.getId().toString(),
            userId.toString(),
            hold.getAmountKobo(),
            hold.getReference(),
            now.toEpochMilli()));
  }

  @Override
  @Transactional
  public void captureHolds(List<UUID> holdIds) {
    List<Hold> holds = holdRepository.findAllById(holdIds);
    if (holds.size() != holdIds.size()) {
      throw new NotFoundException("HOLD_NOT_FOUND", "One or more holds do not exist");
    }
    for (Hold hold : holds) {
      if (hold.getStatus() != HoldStatus.ACTIVE) {
        throw new UnprocessableEntityException(
            "HOLD_NOT_ACTIVE", "Hold " + hold.getId() + " is not ACTIVE (status: " + hold.getStatus() + ")");
      }
    }
    Instant now = Instant.now();
    for (Hold hold : holds) {
      hold.setStatus(HoldStatus.CONVERTED);
    }
    holdRepository.saveAll(holds);
  }

  @Override
  @Transactional
  public void restoreHolds(List<UUID> holdIds) {
    List<Hold> holds = holdRepository.findAllById(holdIds);
    for (Hold hold : holds) {
      if (hold.getStatus() == HoldStatus.CONVERTED) {
        hold.setStatus(HoldStatus.ACTIVE);
      }
    }
    holdRepository.saveAll(holds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<HoldResponse> listActiveHolds(UUID userId) {
    // findWallet, not getOrCreateWallet: this is a read-only transaction, and getOrCreateWallet's
    // row lock would be rejected by Postgres under one. No wallet row yet also just means no
    // holds exist yet, not an error.
    return walletService
        .findWallet(userId)
        .map(
            wallet ->
                holdMapper.toResponseList(
                    holdRepository.findAllByWalletIdAndStatusOrderByCreatedAtDesc(
                        wallet.getId(), HoldStatus.ACTIVE)))
        .orElseGet(List::of);
  }
}

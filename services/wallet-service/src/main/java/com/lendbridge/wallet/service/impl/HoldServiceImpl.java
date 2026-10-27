package com.lendbridge.wallet.service.impl;

import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
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
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletHoldCreatedPayload;
import com.lendbridge.wallet.outbox.WalletHoldReleasedPayload;
import com.lendbridge.wallet.repository.HoldRepository;
import com.lendbridge.wallet.service.HoldService;
import com.lendbridge.wallet.service.WalletService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

  @Override
  @Transactional
  public HoldResponse createHold(UUID userId, CreateHoldRequest request, String idempotencyKey) {
    Optional<Hold> existing = holdRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return holdMapper.toResponse(existing.get());
    }

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

    try {
      holdRepository.save(hold);
    } catch (DataIntegrityViolationException e) {
      return holdRepository
          .findByIdempotencyKey(idempotencyKey)
          .map(holdMapper::toResponse)
          .orElseThrow(() -> e);
    }

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

    return holdMapper.toResponse(hold);
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

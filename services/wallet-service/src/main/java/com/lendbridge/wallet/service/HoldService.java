package com.lendbridge.wallet.service;

import com.lendbridge.wallet.dto.CreateHoldRequest;
import com.lendbridge.wallet.dto.HoldResponse;
import java.util.List;
import java.util.UUID;

public interface HoldService {

  HoldResponse createHold(UUID userId, CreateHoldRequest request, String idempotencyKey);

  void releaseHold(UUID userId, UUID holdId);

  List<HoldResponse> listActiveHolds(UUID userId);

  /** Flips every named hold {@code ACTIVE -> CONVERTED} — the funding saga's "convert a lender's
   * hold into a real debit" step (the actual ledger posting happens separately, in ledger-service;
   * this is purely the local status flip that marks the hold's lifecycle settled). Throws if any
   * hold isn't found or isn't {@code ACTIVE}, and flips none of them in that case — an all-or-
   * nothing batch, same reasoning as a journal entry's debits/credits being validated before any
   * posting is persisted. */
  void captureHolds(List<UUID> holdIds);

  /** The funding saga's one compensating action: flips {@code CONVERTED} holds back to
   * {@code ACTIVE} if disbursement didn't complete after capture. Already-{@code ACTIVE} holds are
   * left alone (idempotent against redelivery), and a hold already {@code RELEASED} is left alone
   * too — there's nothing sensible to restore it to. */
  void restoreHolds(List<UUID> holdIds);
}

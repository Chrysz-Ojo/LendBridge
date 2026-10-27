package com.lendbridge.wallet.service;

import com.lendbridge.wallet.dto.CreateHoldRequest;
import com.lendbridge.wallet.dto.HoldResponse;
import java.util.List;
import java.util.UUID;

public interface HoldService {

  HoldResponse createHold(UUID userId, CreateHoldRequest request, String idempotencyKey);

  void releaseHold(UUID userId, UUID holdId);

  List<HoldResponse> listActiveHolds(UUID userId);
}

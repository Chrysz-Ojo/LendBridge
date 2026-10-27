package com.lendbridge.payment.service;

import com.lendbridge.payment.dto.TopUpRequest;
import com.lendbridge.payment.dto.TopUpResponse;
import com.lendbridge.payment.dto.VirtualAccountTopUpRequest;
import com.lendbridge.payment.dto.VirtualAccountTopUpResponse;
import java.util.UUID;

public interface TopUpService {

  TopUpResponse createTopUp(UUID userId, TopUpRequest request, String idempotencyKey);

  TopUpResponse getTopUp(UUID userId, UUID id);

  VirtualAccountTopUpResponse createVirtualAccount(
      UUID userId, VirtualAccountTopUpRequest request, String idempotencyKey);
}

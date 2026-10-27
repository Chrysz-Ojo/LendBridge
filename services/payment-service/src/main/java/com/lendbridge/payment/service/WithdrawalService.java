package com.lendbridge.payment.service;

import com.lendbridge.payment.dto.WithdrawalRequest;
import com.lendbridge.payment.dto.WithdrawalResponse;
import java.util.UUID;

public interface WithdrawalService {

  WithdrawalResponse createWithdrawal(UUID userId, WithdrawalRequest request, String idempotencyKey);

  WithdrawalResponse getWithdrawal(UUID userId, UUID id);
}

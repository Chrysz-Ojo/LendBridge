package com.lendbridge.payment.controller;

import com.lendbridge.payment.dto.WithdrawalRequest;
import com.lendbridge.payment.dto.WithdrawalResponse;
import com.lendbridge.payment.security.CurrentUser;
import com.lendbridge.payment.service.WithdrawalService;
import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.exception.BadRequestException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

  private final WithdrawalService withdrawalService;

  @PostMapping
  public ResponseEntity<ApiResponse<WithdrawalResponse>> createWithdrawal(
      Authentication authentication,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody WithdrawalRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BadRequestException(
          "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    }
    WithdrawalResponse response =
        withdrawalService.createWithdrawal(
            CurrentUser.idFrom(authentication), request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<WithdrawalResponse>> getWithdrawal(
      Authentication authentication, @PathVariable UUID id) {
    WithdrawalResponse response =
        withdrawalService.getWithdrawal(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

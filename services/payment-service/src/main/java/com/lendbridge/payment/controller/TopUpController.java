package com.lendbridge.payment.controller;

import com.lendbridge.payment.dto.TopUpRequest;
import com.lendbridge.payment.dto.TopUpResponse;
import com.lendbridge.payment.dto.VirtualAccountTopUpRequest;
import com.lendbridge.payment.dto.VirtualAccountTopUpResponse;
import com.lendbridge.payment.security.CurrentUser;
import com.lendbridge.payment.service.TopUpService;
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
@RequestMapping("/api/v1/payments/top-ups")
@RequiredArgsConstructor
public class TopUpController {

  private final TopUpService topUpService;

  @PostMapping
  public ResponseEntity<ApiResponse<TopUpResponse>> createTopUp(
      Authentication authentication,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TopUpRequest request) {
    requireIdempotencyKey(idempotencyKey);
    TopUpResponse response =
        topUpService.createTopUp(CurrentUser.idFrom(authentication), request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<TopUpResponse>> getTopUp(
      Authentication authentication, @PathVariable UUID id) {
    TopUpResponse response = topUpService.getTopUp(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/virtual-accounts")
  public ResponseEntity<ApiResponse<VirtualAccountTopUpResponse>> createVirtualAccount(
      Authentication authentication,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody VirtualAccountTopUpRequest request) {
    requireIdempotencyKey(idempotencyKey);
    VirtualAccountTopUpResponse response =
        topUpService.createVirtualAccount(
            CurrentUser.idFrom(authentication), request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BadRequestException(
          "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    }
  }
}

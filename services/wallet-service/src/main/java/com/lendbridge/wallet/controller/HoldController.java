package com.lendbridge.wallet.controller;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.wallet.dto.CreateHoldRequest;
import com.lendbridge.wallet.dto.HoldResponse;
import com.lendbridge.wallet.security.CurrentUser;
import com.lendbridge.wallet.service.HoldService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets/me/holds")
@RequiredArgsConstructor
public class HoldController {

  private final HoldService holdService;

  @PostMapping
  public ResponseEntity<ApiResponse<HoldResponse>> createHold(
      Authentication authentication,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateHoldRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    }
    HoldResponse response =
        holdService.createHold(CurrentUser.idFrom(authentication), request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> releaseHold(Authentication authentication, @PathVariable UUID id) {
    holdService.releaseHold(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<HoldResponse>>> listHolds(Authentication authentication) {
    List<HoldResponse> response = holdService.listActiveHolds(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

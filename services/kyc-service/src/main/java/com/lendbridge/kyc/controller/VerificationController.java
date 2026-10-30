package com.lendbridge.kyc.controller;

import com.lendbridge.kyc.dto.StartVerificationRequest;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.security.CurrentUser;
import com.lendbridge.kyc.service.VerificationService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Borrower-facing: start a case and check its status. No {@code Idempotency-Key} header — CLAUDE.md
 * scopes that rule to money-moving POSTs; a duplicate {@code POST} while a case is still open
 * instead replays the existing case via {@code IdempotencyOperations}, keyed on the caller's own
 * JWT subject (see the approved plan's design decisions).
 */
@RestController
@RequestMapping("/api/v1/kyc/verifications")
@RequiredArgsConstructor
public class VerificationController {

  private final VerificationService verificationService;

  @PostMapping
  public ResponseEntity<ApiResponse<VerificationStatusResponse>> startVerification(
      Authentication authentication, @Valid @RequestBody StartVerificationRequest request) {
    VerificationStatusResponse response =
        verificationService.startVerification(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<VerificationStatusResponse>> getMyStatus(Authentication authentication) {
    VerificationStatusResponse response = verificationService.getStatus(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

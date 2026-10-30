package com.lendbridge.investment.controller;

import com.lendbridge.investment.dto.CommitmentRequest;
import com.lendbridge.investment.dto.CommitmentResponse;
import com.lendbridge.investment.security.CurrentUser;
import com.lendbridge.investment.service.CommitmentService;
import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.exception.BadRequestException;
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
@RequestMapping("/api/v1/investment")
@RequiredArgsConstructor
public class CommitmentController {

  private final CommitmentService commitmentService;

  @PostMapping("/loans/{loanId}/commitments")
  public ResponseEntity<ApiResponse<CommitmentResponse>> create(
      Authentication authentication,
      @PathVariable UUID loanId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CommitmentRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    }
    CommitmentResponse response =
        commitmentService.create(CurrentUser.idFrom(authentication), loanId, request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @DeleteMapping("/commitments/{id}")
  public ResponseEntity<ApiResponse<CommitmentResponse>> cancel(Authentication authentication, @PathVariable UUID id) {
    CommitmentResponse response = commitmentService.cancel(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/me/commitments")
  public ResponseEntity<ApiResponse<List<CommitmentResponse>>> listMine(Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(commitmentService.listMine(CurrentUser.idFrom(authentication))));
  }
}

package com.lendbridge.ledger.controller;

import com.lendbridge.ledger.dto.AccountBalanceResponse;
import com.lendbridge.ledger.dto.AccountResponse;
import com.lendbridge.ledger.dto.OpenAccountRequest;
import com.lendbridge.ledger.dto.PostingPageResponse;
import com.lendbridge.ledger.service.AccountService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger/accounts")
@RequiredArgsConstructor
public class AccountController {

  private static final int MAX_POSTINGS_PAGE_SIZE = 200;

  private final AccountService accountService;

  @PostMapping
  public ResponseEntity<ApiResponse<AccountResponse>> openAccount(@Valid @RequestBody OpenAccountRequest request) {
    AccountResponse response = accountService.openAccount(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(accountService.getAccount(id)));
  }

  @GetMapping("/{id}/balance")
  public ResponseEntity<ApiResponse<AccountBalanceResponse>> getBalance(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(accountService.getBalance(id)));
  }

  @GetMapping("/{id}/postings")
  public ResponseEntity<ApiResponse<PostingPageResponse>> listPostings(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID after,
      @RequestParam(defaultValue = "50") int limit) {
    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_POSTINGS_PAGE_SIZE);
    return ResponseEntity.ok(ApiResponse.success(accountService.listPostings(id, after, effectiveLimit)));
  }
}

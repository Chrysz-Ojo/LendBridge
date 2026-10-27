package com.lendbridge.wallet.controller;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.wallet.dto.AddBankAccountRequest;
import com.lendbridge.wallet.dto.BankAccountResponse;
import com.lendbridge.wallet.security.CurrentUser;
import com.lendbridge.wallet.service.BankAccountService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets/me/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

  private final BankAccountService bankAccountService;

  @PostMapping
  public ResponseEntity<ApiResponse<BankAccountResponse>> addBankAccount(
      Authentication authentication, @Valid @RequestBody AddBankAccountRequest request) {
    BankAccountResponse response =
        bankAccountService.addBankAccount(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<BankAccountResponse>>> listBankAccounts(
      Authentication authentication) {
    List<BankAccountResponse> response =
        bankAccountService.listBankAccounts(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

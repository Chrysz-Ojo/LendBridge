package com.lendbridge.wallet.controller;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.wallet.dto.WalletResponse;
import com.lendbridge.wallet.security.CurrentUser;
import com.lendbridge.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets/me")
@RequiredArgsConstructor
public class WalletController {

  private final WalletService walletService;

  @GetMapping
  public ResponseEntity<ApiResponse<WalletResponse>> getWallet(Authentication authentication) {
    WalletResponse response = walletService.getWalletSummary(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

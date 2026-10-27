package com.lendbridge.wallet.controller;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.wallet.dto.RegisterVirtualAccountRequest;
import com.lendbridge.wallet.dto.VirtualAccountResponse;
import com.lendbridge.wallet.security.CurrentUser;
import com.lendbridge.wallet.service.VirtualAccountService;
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
@RequestMapping("/api/v1/wallets/me/virtual-accounts")
@RequiredArgsConstructor
public class VirtualAccountController {

  private final VirtualAccountService virtualAccountService;

  @PostMapping
  public ResponseEntity<ApiResponse<VirtualAccountResponse>> registerVirtualAccount(
      Authentication authentication, @Valid @RequestBody RegisterVirtualAccountRequest request) {
    VirtualAccountResponse response =
        virtualAccountService.registerVirtualAccount(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<VirtualAccountResponse>>> listVirtualAccounts(
      Authentication authentication) {
    List<VirtualAccountResponse> response =
        virtualAccountService.listVirtualAccounts(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}

package com.lendbridge.payment.wallet;

import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "wallet-service",
    url = "${WALLET_SERVICE_URL:http://localhost:8091}",
    configuration = WalletFeignConfig.class)
public interface WalletClient {

  @GetMapping("/api/v1/wallets/me")
  ApiResponse<WalletSummaryResponse> getWalletSummary();

  @GetMapping("/api/v1/wallets/me/bank-accounts/{id}")
  ApiResponse<BankAccountPayoutDetails> getBankAccountForPayout(@PathVariable("id") UUID id);

  @PostMapping("/api/v1/wallets/me/virtual-accounts")
  ApiResponse<Object> registerVirtualAccount(@RequestBody RegisterVirtualAccountCommand request);
}

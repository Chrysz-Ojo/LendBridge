package com.lendbridge.investment.wallet;

import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "wallet-service",
    url = "${WALLET_SERVICE_URL:http://localhost:8091}",
    configuration = WalletFeignConfig.class)
public interface WalletClient {

  @PostMapping("/api/v1/wallets/me/holds")
  ApiResponse<HoldResult> createHold(
      @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody CreateHoldCommand request);

  @DeleteMapping("/api/v1/wallets/me/holds/{id}")
  ApiResponse<Void> releaseHold(@PathVariable("id") UUID id);
}

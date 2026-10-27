package com.lendbridge.wallet.ledger;

import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "ledger-service",
    url = "${LEDGER_SERVICE_URL:http://localhost:8090}",
    configuration = LedgerFeignConfig.class)
public interface LedgerClient {

  @PostMapping("/api/v1/ledger/accounts")
  ApiResponse<LedgerAccountResponse> openAccount(@RequestBody OpenLedgerAccountRequest request);

  @GetMapping("/api/v1/ledger/accounts/{id}/balance")
  ApiResponse<LedgerBalanceResponse> getBalance(@PathVariable("id") UUID accountId);
}

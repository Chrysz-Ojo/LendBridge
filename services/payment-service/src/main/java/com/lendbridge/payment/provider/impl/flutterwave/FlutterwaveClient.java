package com.lendbridge.payment.provider.impl.flutterwave;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** v3 API — recommended over the v4 public-beta, which is missing features this adapter needs
 * (confirmed via direct review of Flutterwave's current docs). */
@FeignClient(name = "flutterwave", url = "${flutterwave.base-url}", configuration = FlutterwaveFeignConfig.class)
public interface FlutterwaveClient {

  @PostMapping("/payments")
  JsonNode initiatePayment(@RequestBody Map<String, Object> request);

  @GetMapping("/transactions/verify_by_reference")
  JsonNode verifyByReference(@RequestParam("tx_ref") String txRef);

  @PostMapping("/virtual-account-numbers")
  JsonNode createVirtualAccount(@RequestBody Map<String, Object> request);

  @PostMapping("/transfers")
  JsonNode initiateTransfer(@RequestBody Map<String, Object> request);
}

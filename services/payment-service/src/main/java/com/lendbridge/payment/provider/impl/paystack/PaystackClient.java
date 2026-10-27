package com.lendbridge.payment.provider.impl.paystack;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Thin REST mapping of the exact Paystack endpoints this adapter needs — request/response
 * shapes are decoded as {@link JsonNode} since these are raw third-party payloads, not this
 * service's own DTOs; the normalized {@code Provider*Result} types are the real contract. */
@FeignClient(name = "paystack", url = "${paystack.base-url}", configuration = PaystackFeignConfig.class)
public interface PaystackClient {

  @PostMapping("/transaction/initialize")
  JsonNode initializeTransaction(@RequestBody Map<String, Object> request);

  @GetMapping("/transaction/verify/{reference}")
  JsonNode verifyTransaction(@PathVariable("reference") String reference);

  @PostMapping("/customer")
  JsonNode createCustomer(@RequestBody Map<String, Object> request);

  @PostMapping("/dedicated_account")
  JsonNode createDedicatedAccount(@RequestBody Map<String, Object> request);

  @PostMapping("/transferrecipient")
  JsonNode createTransferRecipient(@RequestBody Map<String, Object> request);

  @PostMapping("/transfer")
  JsonNode initiateTransfer(@RequestBody Map<String, Object> request);
}

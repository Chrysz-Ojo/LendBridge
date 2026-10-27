package com.lendbridge.payment.provider.impl.monnify;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "monnify", url = "${monnify.base-url}", configuration = MonnifyFeignConfig.class)
public interface MonnifyClient {

  @PostMapping("/api/v1/merchant/transactions/init-transaction")
  JsonNode initTransaction(@RequestBody Map<String, Object> request);

  @PostMapping("/api/v1/bank-transfer/reserved-accounts")
  JsonNode reserveAccount(@RequestBody Map<String, Object> request);

  @PostMapping("/api/v2/disbursements/single")
  JsonNode initiateDisbursement(@RequestBody Map<String, Object> request);

  /** Exposed for completeness/documentation — {@link MonnifyPaymentProvider} never calls this
   * itself (see its class javadoc for why). */
  @PostMapping("/api/v2/disbursements/single/validate-otp")
  JsonNode validateDisbursementOtp(@RequestBody Map<String, Object> request);
}

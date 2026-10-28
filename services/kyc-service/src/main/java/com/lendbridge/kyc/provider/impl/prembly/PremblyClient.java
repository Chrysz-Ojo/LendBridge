package com.lendbridge.kyc.provider.impl.prembly;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Thin REST mapping of the Prembly (Identitypass) endpoints this adapter needs — modeled on
 * Identitypass's BVN/NIN verification and face-verification products, built from general knowledge
 * of Prembly's public API shape, not live/current docs (see this service's final report).
 */
@FeignClient(name = "prembly", url = "${prembly.base-url}", configuration = PremblyFeignConfig.class)
public interface PremblyClient {

  @PostMapping("/identitypass/verification/bvn")
  JsonNode verifyBvn(@RequestBody Map<String, Object> request);

  @PostMapping("/identitypass/verification/nin")
  JsonNode verifyNin(@RequestBody Map<String, Object> request);

  @PostMapping("/identitypass/verification/face_verification")
  JsonNode verifyFaceMatch(@RequestBody Map<String, Object> request);
}

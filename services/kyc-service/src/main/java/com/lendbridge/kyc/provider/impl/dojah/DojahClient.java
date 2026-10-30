package com.lendbridge.kyc.provider.impl.dojah;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Thin REST mapping of the Dojah endpoints this adapter needs — modeled on Dojah's KYC lookup
 * (BVN/NIN) and Photo-ID verification (liveness + selfie-vs-ID match) products, built from general
 * knowledge of Dojah's public API shape, not live/current docs (see this service's final report).
 */
@FeignClient(name = "dojah", url = "${dojah.base-url}", configuration = DojahFeignConfig.class)
public interface DojahClient {

  @GetMapping("/api/v1/kyc/bvn")
  JsonNode lookupBvn(@RequestParam("bvn") String bvn);

  @GetMapping("/api/v1/kyc/nin")
  JsonNode lookupNin(@RequestParam("nin") String nin);

  @PostMapping("/api/v1/kyc/photoid/verify")
  JsonNode verifyPhotoId(@RequestBody Map<String, Object> request);
}

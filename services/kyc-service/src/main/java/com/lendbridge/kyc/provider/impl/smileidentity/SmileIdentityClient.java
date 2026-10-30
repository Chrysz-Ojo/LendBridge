package com.lendbridge.kyc.provider.impl.smileidentity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Thin REST mapping of the Smile Identity endpoints this adapter needs, modeled on Smile ID's
 * "Enhanced KYC" (id-lookup) and "SmartSelfie Verification" (liveness + selfie-vs-ID match)
 * products — built from general knowledge of Smile ID's public API shape, not live/current docs
 * (see the approved plan and this service's final report). Request/response shapes are decoded as
 * {@link JsonNode} since these are raw third-party payloads, not this service's own DTOs; the
 * normalized {@code ProviderCheckResult} is the real contract.
 */
@FeignClient(
    name = "smile-identity",
    url = "${smileidentity.base-url}",
    configuration = SmileIdentityFeignConfig.class)
public interface SmileIdentityClient {

  /** Enhanced KYC: a synchronous id-lookup job against a national ID registry (BVN or NIN,
   * selected by {@code id_info.id_type}). */
  @PostMapping("/v1/id_verification")
  JsonNode submitIdVerification(@RequestBody Map<String, Object> request);

  /** SmartSelfie Verification: liveness + selfie-vs-ID-photo match as a single job. */
  @PostMapping("/v1/smart_selfie_verification")
  JsonNode submitSmartSelfieVerification(@RequestBody Map<String, Object> request);
}

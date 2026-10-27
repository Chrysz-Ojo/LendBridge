package com.lendbridge.payment.provider.impl.monnify;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** Deliberately has no shared {@code @Configuration} interceptor — the Basic-auth header for
 * login is fundamentally different from every other Monnify call's Bearer token, so it's passed
 * explicitly per-call instead of injected globally. */
@FeignClient(name = "monnify-auth", url = "${monnify.base-url}")
public interface MonnifyAuthClient {

  @PostMapping("/api/v1/auth/login")
  JsonNode login(@RequestHeader("Authorization") String basicAuthHeader);
}

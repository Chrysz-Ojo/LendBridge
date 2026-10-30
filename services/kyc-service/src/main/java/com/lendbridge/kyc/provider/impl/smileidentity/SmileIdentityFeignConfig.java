package com.lendbridge.kyc.provider.impl.smileidentity;

import feign.RequestInterceptor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * Smile ID authenticates with a partner_id + a per-request HMAC-SHA256 signature (computed over a
 * timestamp) rather than a single static bearer token — modeled from general knowledge of Smile
 * ID's partner-signature scheme, not confirmed against live docs this session (see this service's
 * final report).
 *
 * <p>Deliberately NOT {@code @Configuration} — only referenced via
 * {@code @FeignClient(configuration = SmileIdentityFeignConfig.class)}; see
 * {@code DocumentFeignConfig}'s javadoc for why a global Feign-interceptor bean would be a real bug
 * in a service with several Feign clients, and {@code FeignConfigurationIsolationIT} for the
 * regression test.
 */
public class SmileIdentityFeignConfig {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  @Bean
  public RequestInterceptor smileIdentityAuthInterceptor(
      @Value("${smileidentity.partner-id}") String partnerId,
      @Value("${smileidentity.api-key}") String apiKey) {
    return requestTemplate -> {
      String timestamp = Instant.now().toString();
      String signature = sign(apiKey, partnerId, timestamp);
      requestTemplate.header("smile-partner-id", partnerId);
      requestTemplate.header("smile-timestamp", timestamp);
      requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + signature);
    };
  }

  private String sign(String apiKey, String partnerId, String timestamp) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] computed = mac.doFinal((partnerId + timestamp).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(computed);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to sign Smile Identity request", e);
    }
  }
}

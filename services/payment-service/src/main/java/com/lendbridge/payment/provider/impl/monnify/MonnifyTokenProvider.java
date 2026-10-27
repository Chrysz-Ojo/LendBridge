package com.lendbridge.payment.provider.impl.monnify;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caches Monnify's OAuth2-style bearer token in memory per instance (not Redis — this is a
 * per-process credential, not shared state; an accepted simplification, not every cache needs to
 * be distributed). Tokens last 1 hour per Monnify's docs; refreshed with a small safety margin so
 * a request never races an about-to-expire token.
 */
@Component
public class MonnifyTokenProvider {

  private static final long SAFETY_MARGIN_SECONDS = 30;

  private final MonnifyAuthClient authClient;
  private final String apiKey;
  private final String clientSecret;

  private volatile String cachedToken;
  private volatile Instant expiresAt = Instant.EPOCH;

  public MonnifyTokenProvider(
      MonnifyAuthClient authClient,
      @Value("${monnify.api-key}") String apiKey,
      @Value("${monnify.client-secret}") String clientSecret) {
    this.authClient = authClient;
    this.apiKey = apiKey;
    this.clientSecret = clientSecret;
  }

  public synchronized String getAccessToken() {
    if (cachedToken == null || Instant.now().isAfter(expiresAt.minusSeconds(SAFETY_MARGIN_SECONDS))) {
      refresh();
    }
    return cachedToken;
  }

  private void refresh() {
    String credentials = apiKey + ":" + clientSecret;
    String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    JsonNode response = authClient.login(basicAuth);
    JsonNode body = response.path("responseBody");
    cachedToken = body.path("accessToken").asText();
    long expiresInSeconds = body.path("expiresIn").asLong(3600);
    expiresAt = Instant.now().plusSeconds(expiresInSeconds);
  }
}

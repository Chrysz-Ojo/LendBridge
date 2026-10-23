package com.lendbridge.authentication.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * High-entropy opaque tokens for refresh tokens, password-reset tokens and email-verification
 * tokens. The raw value is returned to the caller exactly once; only its SHA-256 hash is ever
 * persisted.
 */
@Component
public class OpaqueTokenGenerator {

  private static final int TOKEN_BYTES = 32;
  private final SecureRandom secureRandom = new SecureRandom();

  public String generateRaw() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}

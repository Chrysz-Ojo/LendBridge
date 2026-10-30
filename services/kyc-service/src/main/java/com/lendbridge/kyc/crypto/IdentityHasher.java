package com.lendbridge.kyc.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deterministic HMAC-SHA256(value, pepper) hash stored alongside the AES-GCM encrypted BVN/NIN
 * value, since GCM ciphertext is non-deterministic and cannot be used for exact-match/duplicate
 * lookups — same precedent as authentication-service's {@code HmacPhoneHasher}, applied here to
 * satisfy the PRD's "BVN is also stored as a salted hash for lookups" rule (extended to NIN per
 * ADR 0014).
 */
@Component
public class IdentityHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec pepperKey;

  public IdentityHasher(@Value("${kyc.identity-hash.pepper}") String pepper) {
    this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  public String hash(String normalizedValue) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(pepperKey);
      byte[] result = mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(result);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Unable to hash identity value", e);
    }
  }
}

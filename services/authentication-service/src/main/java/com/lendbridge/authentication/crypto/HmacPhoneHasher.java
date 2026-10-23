package com.lendbridge.authentication.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deterministic HMAC-SHA256(phone, pepper) hash used as a unique-constraint/lookup column
 * alongside the AES-GCM encrypted phone value, since GCM ciphertext is non-deterministic and
 * cannot be used for exact-match lookups.
 */
@Component
public class HmacPhoneHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec pepperKey;

  public HmacPhoneHasher(@Value("${auth.phone-hash.pepper}") String pepper) {
    this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  public String hash(String normalizedPhone) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(pepperKey);
      byte[] result = mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(result);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Unable to hash phone number", e);
    }
  }
}

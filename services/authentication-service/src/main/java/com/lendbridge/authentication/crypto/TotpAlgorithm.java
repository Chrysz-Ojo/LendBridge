package com.lendbridge.authentication.crypto;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hand-rolled RFC 6238 TOTP over {@code javax.crypto} HmacSHA1 (no TOTP library added, per
 * CLAUDE.md). Validated in tests against the RFC's own published test vectors.
 */
public final class TotpAlgorithm {

  private static final String ALGORITHM = "HmacSHA1";
  public static final int DEFAULT_TIME_STEP_SECONDS = 30;
  public static final int DEFAULT_DIGITS = 6;

  private TotpAlgorithm() {}

  public static String generateAtTime(byte[] secret, long epochSeconds, int timeStepSeconds, int digits) {
    long counter = epochSeconds / timeStepSeconds;
    return generateForCounter(secret, counter, digits);
  }

  public static boolean verify(
      byte[] secret, String code, int timeStepSeconds, int digits, int allowedStepsDrift) {
    long currentCounter = Instant.now().getEpochSecond() / timeStepSeconds;
    for (long i = -allowedStepsDrift; i <= allowedStepsDrift; i++) {
      String candidate = generateForCounter(secret, currentCounter + i, digits);
      if (candidate.equals(code)) {
        return true;
      }
    }
    return false;
  }

  private static String generateForCounter(byte[] secret, long counter, int digits) {
    byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      byte[] hash = mac.doFinal(counterBytes);
      int offset = hash[hash.length - 1] & 0x0F;
      int binary =
          ((hash[offset] & 0x7F) << 24)
              | ((hash[offset + 1] & 0xFF) << 16)
              | ((hash[offset + 2] & 0xFF) << 8)
              | (hash[offset + 3] & 0xFF);
      int otp = (int) (binary % (int) Math.pow(10, digits));
      return String.format("%0" + digits + "d", otp);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Unable to compute TOTP", e);
    }
  }
}

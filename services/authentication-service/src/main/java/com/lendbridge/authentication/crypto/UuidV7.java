package com.lendbridge.authentication.crypto;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Hand-rolled UUIDv7 generator (draft-ietf-uuidrev layout): 48-bit unix-ms timestamp, a 4-bit
 * version nibble (7), a 2-bit IETF variant, and the remaining bits random. No UUID library is
 * added per CLAUDE.md.
 */
public final class UuidV7 {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private UuidV7() {}

  public static UUID generate() {
    long unixMillis = System.currentTimeMillis();
    byte[] random = new byte[10];
    SECURE_RANDOM.nextBytes(random);

    byte[] bytes = new byte[16];
    bytes[0] = (byte) (unixMillis >>> 40);
    bytes[1] = (byte) (unixMillis >>> 32);
    bytes[2] = (byte) (unixMillis >>> 24);
    bytes[3] = (byte) (unixMillis >>> 16);
    bytes[4] = (byte) (unixMillis >>> 8);
    bytes[5] = (byte) unixMillis;

    bytes[6] = (byte) (0x70 | (random[0] & 0x0F));
    bytes[7] = random[1];
    bytes[8] = (byte) (0x80 | (random[2] & 0x3F));
    bytes[9] = random[3];
    System.arraycopy(random, 4, bytes, 10, 6);

    long mostSigBits = 0;
    for (int i = 0; i < 8; i++) {
      mostSigBits = (mostSigBits << 8) | (bytes[i] & 0xFF);
    }
    long leastSigBits = 0;
    for (int i = 8; i < 16; i++) {
      leastSigBits = (leastSigBits << 8) | (bytes[i] & 0xFF);
    }
    return new UUID(mostSigBits, leastSigBits);
  }
}

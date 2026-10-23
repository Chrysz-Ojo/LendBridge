package com.lendbridge.authentication.crypto;

import java.util.Locale;

/**
 * Hand-rolled RFC 4648 Base32 (standard alphabet, with padding). Used only to render/parse TOTP
 * secrets; no external codec library is added per CLAUDE.md.
 */
public final class Base32 {

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final char PAD = '=';

  private Base32() {}

  public static String encode(byte[] data) {
    if (data.length == 0) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bitsInBuffer = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsInBuffer += 8;
      while (bitsInBuffer >= 5) {
        int index = (buffer >> (bitsInBuffer - 5)) & 0x1F;
        out.append(ALPHABET.charAt(index));
        bitsInBuffer -= 5;
      }
    }
    if (bitsInBuffer > 0) {
      int index = (buffer << (5 - bitsInBuffer)) & 0x1F;
      out.append(ALPHABET.charAt(index));
    }
    while (out.length() % 8 != 0) {
      out.append(PAD);
    }
    return out.toString();
  }

  public static byte[] decode(String encoded) {
    String normalized = encoded.trim().toUpperCase(Locale.ROOT).replace("-", "");
    int padStart = normalized.indexOf(PAD);
    String withoutPadding = padStart >= 0 ? normalized.substring(0, padStart) : normalized;
    if (withoutPadding.isEmpty()) {
      return new byte[0];
    }

    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    int buffer = 0;
    int bitsInBuffer = 0;
    for (char c : withoutPadding.toCharArray()) {
      int index = ALPHABET.indexOf(c);
      if (index < 0) {
        throw new IllegalArgumentException("Invalid Base32 character: " + c);
      }
      buffer = (buffer << 5) | index;
      bitsInBuffer += 5;
      if (bitsInBuffer >= 8) {
        int b = (buffer >> (bitsInBuffer - 8)) & 0xFF;
        out.write(b);
        bitsInBuffer -= 8;
      }
    }
    return out.toByteArray();
  }
}

package com.lendbridge.messaging.masking;

import java.util.regex.Pattern;

/**
 * Best-effort regex redaction of contact info embedded in free-form chat text (PRD E9-S1:
 * "contact details, account numbers and links auto-masked"). No reusable PII-scanning utility
 * exists elsewhere in the repo — every existing {@code mask()} helper (e.g. wallet-service's
 * account-number mask) truncates one already-identified structured field, not free text. This is
 * deliberately simple pattern matching, not exhaustive PII data-loss-prevention — documented as
 * such in ADR 0019.
 */
public final class ContactInfoMasker {

  private static final String REDACTED = "[redacted]";

  private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");

  /** Nigerian local (0 + 10 digits) or E.164-ish (+234/234 + 10 digits) mobile numbers — network
   * prefixes are 70/80/81/90/91 etc, i.e. second digit 7-9, third digit 0-1. Uses digit
   * lookaround instead of {@code \b}, since {@code \b} doesn't anchor cleanly right before a
   * literal {@code +} (both non-word characters, so no boundary is detected there). */
  private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?234|0)[789][01]\\d{8}(?!\\d)");

  /** Any other bare 10-11 digit run — catches account numbers, BVN-shaped numbers, etc. */
  private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\b\\d{10,11}\\b");

  private ContactInfoMasker() {}

  public static String mask(String text) {
    if (text == null) {
      return null;
    }
    String masked = EMAIL.matcher(text).replaceAll(REDACTED);
    masked = URL.matcher(masked).replaceAll(REDACTED);
    masked = PHONE.matcher(masked).replaceAll(REDACTED);
    masked = LONG_DIGIT_RUN.matcher(masked).replaceAll(REDACTED);
    return masked;
  }
}

package com.lendbridge.authentication.service;

import com.lendbridge.authentication.model.OtpPurpose;
import java.util.UUID;

/**
 * Shared one-time-code primitive used by both phone verification (registration) and SMS-based
 * MFA. Codes are 6 digits, expire after 5 minutes, allow at most 5 attempts, and are stored only
 * as a SHA-256 hash.
 */
public interface OtpService {

  void issue(UUID userId, OtpPurpose purpose, String destinationPhone);

  void verify(UUID userId, OtpPurpose purpose, String code);
}

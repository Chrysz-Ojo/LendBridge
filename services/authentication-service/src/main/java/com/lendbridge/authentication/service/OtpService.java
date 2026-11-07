package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.OtpIssueRequest;
import com.lendbridge.authentication.model.OtpPurpose;
import java.util.UUID;

/**
 * Shared one-time-code primitive used by phone verification (registration), SMS-based MFA, and
 * transaction-PIN reset. Codes are 6 digits, expire after 5 minutes, allow at most 5 attempts, and
 * are stored only as a SHA-256 hash.
 */
public interface OtpService {

  void issue(UUID userId, OtpIssueRequest request);

  void verify(UUID userId, OtpPurpose purpose, String code);
}

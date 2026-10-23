package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.SmsVerifyRequest;
import java.util.UUID;

/**
 * SMS-based MFA, layered on the shared OTP primitive. The first successful verify both confirms
 * enrollment and satisfies a login-time MFA challenge; later verifies during login are idempotent
 * against the already-ACTIVE enrollment.
 */
public interface SmsMfaService {

  void challenge(UUID userId);

  void verify(UUID userId, SmsVerifyRequest request);

  void disable(UUID userId);
}

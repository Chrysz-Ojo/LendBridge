package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.ChangePinRequest;
import com.lendbridge.authentication.dto.PinResetChallengeRequest;
import com.lendbridge.authentication.dto.PinResetVerifyRequest;
import com.lendbridge.authentication.dto.SetPinRequest;
import com.lendbridge.authentication.dto.VerifyPinRequest;
import java.util.UUID;

public interface PinService {

  void setPin(UUID userId, SetPinRequest request);

  void changePin(UUID userId, ChangePinRequest request);

  /** Verifies the password and sends a one-time code over both email and SMS. */
  void requestPinReset(UUID userId, PinResetChallengeRequest request);

  /** Verifies the code from {@link #requestPinReset} and sets the new PIN. */
  void confirmPinReset(UUID userId, PinResetVerifyRequest request);

  void verifyPin(UUID userId, VerifyPinRequest request);
}

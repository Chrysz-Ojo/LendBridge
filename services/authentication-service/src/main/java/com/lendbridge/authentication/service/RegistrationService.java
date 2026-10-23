package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.RegisterRequest;
import com.lendbridge.authentication.dto.VerifyEmailRequest;
import com.lendbridge.authentication.dto.VerifyPhoneRequest;
import com.lendbridge.authentication.dto.RegisterResponse;

public interface RegistrationService {

  RegisterResponse register(RegisterRequest request);

  void verifyPhone(VerifyPhoneRequest request);

  void verifyEmail(VerifyEmailRequest request);
}

package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.PasswordResetConfirmRequest;
import com.lendbridge.authentication.dto.PasswordResetRequest;

public interface PasswordResetService {

  void request(PasswordResetRequest request);

  void confirm(PasswordResetConfirmRequest request);
}

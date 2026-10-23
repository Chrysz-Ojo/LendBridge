package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.LoginRequest;
import com.lendbridge.authentication.dto.LogoutRequest;
import com.lendbridge.authentication.dto.MfaLoginRequest;
import com.lendbridge.authentication.dto.RefreshRequest;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.dto.TokenResponse;
import java.util.UUID;

/**
 * The session/auth-lifecycle concern for a user: credential check and lockout, MFA challenge
 * completion, refresh-token rotation and reuse detection, logout, and new-device detection. These
 * are one cohesive feature (not independent responsibilities), so they live on a single service.
 */
public interface LoginService {

  LoginResponse login(LoginRequest request, String ipAddress, String deviceInfo);

  TokenResponse completeMfa(MfaLoginRequest request, String ipAddress, String deviceInfo);

  TokenResponse refresh(RefreshRequest request, String ipAddress, String deviceInfo);

  void logout(LogoutRequest request);

  boolean detectNewDevice(UUID userId, String deviceInfo);
}

package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.service.LoginService;

import com.lendbridge.authentication.dto.LoginRequest;
import com.lendbridge.authentication.dto.LogoutRequest;
import com.lendbridge.authentication.dto.MfaLoginRequest;
import com.lendbridge.authentication.dto.RefreshRequest;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.dto.TokenResponse;
import com.lendbridge.authentication.model.LockoutPolicy;
import com.lendbridge.authentication.model.LoginAttempt;
import com.lendbridge.authentication.model.RefreshRevokeReason;
import com.lendbridge.authentication.model.RefreshToken;
import com.lendbridge.authentication.repository.LoginAttemptRepository;
import com.lendbridge.authentication.repository.RefreshTokenRepository;
import com.lendbridge.authentication.dto.SmsVerifyRequest;
import com.lendbridge.authentication.service.SmsMfaService;
import com.lendbridge.authentication.service.TotpService;
import com.lendbridge.authentication.model.MfaMethod;
import com.lendbridge.authentication.service.RoleAssignmentService;
import com.lendbridge.authentication.crypto.HmacPhoneHasher;
import com.lendbridge.authentication.crypto.OpaqueTokenGenerator;
import com.lendbridge.authentication.crypto.UuidV7;
import com.lendbridge.authentication.exception.BadRequestException;
import com.lendbridge.authentication.exception.LockedAccountException;
import com.lendbridge.authentication.exception.UnauthorizedException;
import com.lendbridge.authentication.notification.EmailSender;
import com.lendbridge.authentication.security.JwtIssuer;
import com.lendbridge.authentication.model.Email;
import com.lendbridge.authentication.model.PhoneNumber;
import com.lendbridge.authentication.model.User;
import com.lendbridge.authentication.repository.UserJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

  private static final long REFRESH_TOKEN_TTL_DAYS = 7;

  private final UserJpaRepository userRepository;
  private final LoginAttemptRepository loginAttemptRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final HmacPhoneHasher hmacPhoneHasher;
  private final OpaqueTokenGenerator opaqueTokenGenerator;
  private final JwtIssuer jwtIssuer;
  private final JwtDecoder jwtDecoder;
  private final RoleAssignmentService roleAssignmentService;
  private final TotpService totpService;
  private final SmsMfaService smsMfaService;
  private final EmailSender emailSender;

  @Override
  @Transactional
  public LoginResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
    String identifier = request.getIdentifier();
    User user = findByIdentifier(identifier).orElse(null);

    if (user == null) {
      recordAttempt(null, identifier, false, ipAddress, deviceInfo, "USER_NOT_FOUND");
      throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid credentials");
    }

    Instant now = Instant.now();
    if (user.isCurrentlyLocked(now)) {
      recordAttempt(user.getId(), identifier, false, ipAddress, deviceInfo, "ACCOUNT_LOCKED");
      throw new LockedAccountException("Account is locked until " + user.getLockedUntil());
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
      Instant lockUntil = LockoutPolicy.lockedUntilAfterFailure(user.getFailedLoginAttempts(), now);
      if (lockUntil != null) {
        user.setLockedUntil(lockUntil);
      }
      user.setUpdatedAt(now);
      userRepository.save(user);
      recordAttempt(user.getId(), identifier, false, ipAddress, deviceInfo, "BAD_PASSWORD");
      throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid credentials");
    }

    boolean newDevice = detectNewDevice(user.getId(), deviceInfo);

    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);
    user.setUpdatedAt(now);
    userRepository.save(user);
    recordAttempt(user.getId(), identifier, true, ipAddress, deviceInfo, null);

    if (newDevice) {
      emailSender.send(
          user.getEmail(),
          "New sign-in to your LendBridge account",
          "We noticed a login to your account from a new device or location.");
    }

    if (user.isMfaEnabled()) {
      return LoginResponse.mfaRequired(jwtIssuer.issueMfaChallengeToken(user.getId()));
    }
    return LoginResponse.issued(issueTokens(user, deviceInfo, ipAddress));
  }


  @Override
  @Transactional
  public TokenResponse completeMfa(MfaLoginRequest request, String ipAddress, String deviceInfo) {
    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(request.getChallengeToken());
    } catch (JwtException e) {
      throw new UnauthorizedException("MFA_CHALLENGE_INVALID", "Invalid or expired MFA challenge");
    }
    if (!JwtIssuer.PURPOSE_MFA_CHALLENGE.equals(jwt.getClaimAsString(JwtIssuer.CLAIM_PURPOSE))) {
      throw new UnauthorizedException("MFA_CHALLENGE_INVALID", "Invalid MFA challenge token");
    }
    UUID userId = UUID.fromString(jwt.getSubject());

    if (request.getMethod() == MfaMethod.TOTP) {
      if (!totpService.verifyActive(userId, request.getCode())) {
        throw new BadRequestException("MFA_CODE_INVALID", "Incorrect TOTP code");
      }
    } else {
      smsMfaService.verify(userId, new SmsVerifyRequest(request.getCode()));
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("MFA_CHALLENGE_INVALID", "User not found"));
    return issueTokens(user, deviceInfo, ipAddress);
  }

  @Override
  @Transactional
  public TokenResponse refresh(RefreshRequest request, String ipAddress, String deviceInfo) {
    String hash = opaqueTokenGenerator.hash(request.getRefreshToken());
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new UnauthorizedException("REFRESH_TOKEN_INVALID", "Invalid refresh token"));

    if (current.isRevoked()) {
      revokeFamily(current.getFamilyId(), RefreshRevokeReason.REUSE_DETECTED);
      throw new UnauthorizedException(
          "REFRESH_TOKEN_REUSE_DETECTED", "Refresh token reuse detected; all sessions revoked");
    }

    Instant now = Instant.now();
    if (current.isExpired(now)) {
      throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
    }

    User user =
        userRepository
            .findById(current.getUserId())
            .orElseThrow(() -> new UnauthorizedException("REFRESH_TOKEN_INVALID", "User not found"));

    String newRawToken = opaqueTokenGenerator.generateRaw();
    RefreshToken replacement =
        RefreshToken.builder()
            .id(UuidV7.generate())
            .userId(user.getId())
            .tokenHash(opaqueTokenGenerator.hash(newRawToken))
            .familyId(current.getFamilyId())
            .issuedAt(now)
            .expiresAt(now.plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS))
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .build();
    refreshTokenRepository.save(replacement);

    current.setRevokedAt(now);
    current.setRevokedReason(RefreshRevokeReason.ROTATED);
    current.setReplacedById(replacement.getId());
    refreshTokenRepository.save(current);

    List<String> roles = roleAssignmentService.rolesOf(user.getId()).stream().map(Enum::name).toList();
    String accessToken =
        jwtIssuer.issueAccessToken(
            user.getId(), roles, user.isEmailVerified(), user.isPhoneVerified(), user.isMfaEnabled());

    return new TokenResponse(
        accessToken, newRawToken, "Bearer", JwtIssuer.ACCESS_TOKEN_TTL_MINUTES * 60);
  }

  @Override
  @Transactional
  public void logout(LogoutRequest request) {
    String hash = opaqueTokenGenerator.hash(request.getRefreshToken());
    RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);
    if (token == null) {
      return;
    }
    Instant now = Instant.now();
    if (request.isAllDevices()) {
      refreshTokenRepository.findByUserIdAndRevokedAtIsNull(token.getUserId())
          .forEach(t -> revoke(t, now));
    } else if (!token.isRevoked()) {
      revoke(token, now);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean detectNewDevice(UUID userId, String deviceInfo) {
    if (deviceInfo == null || deviceInfo.isBlank()) {
      return false;
    }
    return loginAttemptRepository.findByUserIdAndSuccessTrueOrderByCreatedAtDesc(userId).stream()
        .noneMatch(attempt -> deviceInfo.equals(attempt.getUserAgent()));
  }

  private TokenResponse issueTokens(User user, String deviceInfo, String ipAddress) {
    List<String> roles = roleAssignmentService.rolesOf(user.getId()).stream().map(Enum::name).toList();
    String accessToken =
        jwtIssuer.issueAccessToken(
            user.getId(), roles, user.isEmailVerified(), user.isPhoneVerified(), user.isMfaEnabled());

    Instant now = Instant.now();
    String rawRefresh = opaqueTokenGenerator.generateRaw();
    RefreshToken refreshToken =
        RefreshToken.builder()
            .id(UuidV7.generate())
            .userId(user.getId())
            .tokenHash(opaqueTokenGenerator.hash(rawRefresh))
            .familyId(UuidV7.generate())
            .issuedAt(now)
            .expiresAt(now.plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS))
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .build();
    refreshTokenRepository.save(refreshToken);

    return new TokenResponse(
        accessToken, rawRefresh, "Bearer", JwtIssuer.ACCESS_TOKEN_TTL_MINUTES * 60);
  }

  private void revokeFamily(UUID familyId, RefreshRevokeReason reason) {
    Instant now = Instant.now();
    refreshTokenRepository.findByFamilyId(familyId).stream()
        .filter(token -> !token.isRevoked())
        .forEach(
            token -> {
              token.setRevokedAt(now);
              token.setRevokedReason(reason);
              refreshTokenRepository.save(token);
            });
  }

  private void revoke(RefreshToken token, Instant now) {
    token.setRevokedAt(now);
    token.setRevokedReason(RefreshRevokeReason.LOGOUT);
    refreshTokenRepository.save(token);
  }

  private void recordAttempt(
      UUID userId, String identifier, boolean success, String ipAddress, String userAgent, String failureReason) {
    loginAttemptRepository.save(
        LoginAttempt.builder()
            .id(UuidV7.generate())
            .userId(userId)
            .identifier(identifier)
            .success(success)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .failureReason(failureReason)
            .createdAt(Instant.now())
            .build());
  }

  private Optional<User> findByIdentifier(String identifier) {
    try {
      if (identifier.contains("@")) {
        return userRepository.findByEmailForUpdate(Email.of(identifier).getValue());
      }
      return userRepository.findByPhoneHashForUpdate(
          hmacPhoneHasher.hash(PhoneNumber.of(identifier).getValue()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}

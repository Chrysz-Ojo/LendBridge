package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.service.PasswordResetService;

import com.lendbridge.authentication.model.RefreshRevokeReason;
import com.lendbridge.authentication.repository.RefreshTokenRepository;
import com.lendbridge.authentication.dto.PasswordResetConfirmRequest;
import com.lendbridge.authentication.dto.PasswordResetRequest;
import com.lendbridge.authentication.model.PasswordResetToken;
import com.lendbridge.authentication.repository.PasswordResetTokenRepository;
import com.lendbridge.authentication.crypto.HmacPhoneHasher;
import com.lendbridge.authentication.crypto.OpaqueTokenGenerator;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.authentication.notification.EmailSender;
import com.lendbridge.authentication.model.Email;
import com.lendbridge.authentication.model.Password;
import com.lendbridge.authentication.model.PhoneNumber;
import com.lendbridge.authentication.model.User;
import com.lendbridge.authentication.repository.UserJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

  private static final long RESET_TOKEN_TTL_MINUTES = 30;

  private final UserJpaRepository userRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final HmacPhoneHasher hmacPhoneHasher;
  private final OpaqueTokenGenerator opaqueTokenGenerator;
  private final PasswordEncoder passwordEncoder;
  private final EmailSender emailSender;

  @Value("${auth.password-reset.base-url}")
  private String resetBaseUrl;

  @Override
  @Transactional
  public void request(PasswordResetRequest request) {
    Optional<User> maybeUser = findByIdentifier(request.getIdentifier());
    if (maybeUser.isEmpty()) {
      return;
    }
    User user = maybeUser.get();
    String rawToken = opaqueTokenGenerator.generateRaw();
    PasswordResetToken token =
        PasswordResetToken.builder()
            .id(UuidV7.generate())
            .userId(user.getId())
            .tokenHash(opaqueTokenGenerator.hash(rawToken))
            .expiresAt(Instant.now().plus(RESET_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
            .createdAt(Instant.now())
            .build();
    tokenRepository.save(token);
    emailSender.send(
        user.getEmail(),
        "Reset your LendBridge password",
        "Reset your password: " + resetBaseUrl + "?token=" + rawToken);
  }

  @Override
  @Transactional
  public void confirm(PasswordResetConfirmRequest request) {
    Password validated = Password.of(request.getNewPassword());

    String hash = opaqueTokenGenerator.hash(request.getToken());
    PasswordResetToken token =
        tokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new BadRequestException("TOKEN_INVALID", "Invalid reset token"));
    if (token.isConsumed()) {
      throw new BadRequestException("TOKEN_ALREADY_USED", "Reset token already used");
    }
    if (token.isExpired(Instant.now())) {
      throw new BadRequestException("TOKEN_EXPIRED", "Reset token has expired");
    }

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> new BadRequestException("TOKEN_INVALID", "Invalid reset token"));

    user.setPasswordHash(passwordEncoder.encode(validated.getValue()));
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);

    token.setConsumedAt(Instant.now());
    tokenRepository.save(token);

    Instant now = Instant.now();
    refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId()).forEach(
        rt -> {
          rt.setRevokedAt(now);
          rt.setRevokedReason(RefreshRevokeReason.PASSWORD_RESET);
          refreshTokenRepository.save(rt);
        });
  }

  private Optional<User> findByIdentifier(String identifier) {
    try {
      if (identifier.contains("@")) {
        return userRepository.findByEmail(Email.of(identifier).getValue());
      }
      return userRepository.findByPhoneHash(hmacPhoneHasher.hash(PhoneNumber.of(identifier).getValue()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}

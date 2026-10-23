package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.service.TotpService;

import com.lendbridge.authentication.dto.TotpVerifyRequest;
import com.lendbridge.authentication.dto.TotpEnrollResponse;
import com.lendbridge.authentication.model.MfaEnrollment;
import com.lendbridge.authentication.model.MfaMethod;
import com.lendbridge.authentication.model.MfaStatus;
import com.lendbridge.authentication.crypto.TotpAlgorithm;
import com.lendbridge.authentication.repository.MfaEnrollmentRepository;
import com.lendbridge.authentication.crypto.Base32;
import com.lendbridge.authentication.crypto.FieldCipher;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.authentication.model.User;
import com.lendbridge.authentication.repository.UserJpaRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TotpServiceImpl implements TotpService {

  private static final int SECRET_BYTES = 20;
  private static final int ALLOWED_STEP_DRIFT = 1;

  private final MfaEnrollmentRepository repository;
  private final UserJpaRepository userRepository;
  private final FieldCipher fieldCipher;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public TotpEnrollResponse enroll(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

    repository
        .findByUserIdAndMethodAndStatus(userId, MfaMethod.TOTP, MfaStatus.PENDING)
        .ifPresent(repository::delete);

    byte[] secretBytes = new byte[SECRET_BYTES];
    secureRandom.nextBytes(secretBytes);
    String base32Secret = Base32.encode(secretBytes);

    MfaEnrollment enrollment =
        MfaEnrollment.builder()
            .id(UuidV7.generate())
            .userId(userId)
            .method(MfaMethod.TOTP)
            .secretCiphertext(fieldCipher.encrypt(base32Secret))
            .status(MfaStatus.PENDING)
            .createdAt(Instant.now())
            .build();
    repository.save(enrollment);

    String otpauthUri =
        "otpauth://totp/LendBridge:"
            + java.net.URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
            + "?secret="
            + base32Secret
            + "&issuer=LendBridge&digits="
            + TotpAlgorithm.DEFAULT_DIGITS
            + "&period="
            + TotpAlgorithm.DEFAULT_TIME_STEP_SECONDS;

    return new TotpEnrollResponse(base32Secret, otpauthUri);
  }

  @Override
  @Transactional
  public void verifyAndActivate(UUID userId, TotpVerifyRequest request) {
    MfaEnrollment enrollment =
        repository
            .findByUserIdAndMethodAndStatus(userId, MfaMethod.TOTP, MfaStatus.PENDING)
            .orElseThrow(
                () -> new BadRequestException("MFA_NOT_ENROLLED", "No pending TOTP enrollment"));

    if (!matches(enrollment, request.getCode())) {
      throw new BadRequestException("MFA_CODE_INVALID", "Incorrect TOTP code");
    }

    enrollment.setStatus(MfaStatus.ACTIVE);
    enrollment.setActivatedAt(Instant.now());
    repository.save(enrollment);

    User user = userRepository.findById(userId).orElseThrow();
    user.setMfaEnabled(true);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean verifyActive(UUID userId, String code) {
    return repository
        .findByUserIdAndMethodAndStatus(userId, MfaMethod.TOTP, MfaStatus.ACTIVE)
        .map(enrollment -> matches(enrollment, code))
        .orElse(false);
  }

  @Override
  @Transactional
  public void disable(UUID userId) {
    repository
        .findByUserIdAndMethodAndStatus(userId, MfaMethod.TOTP, MfaStatus.ACTIVE)
        .ifPresent(
            enrollment -> {
              enrollment.setStatus(MfaStatus.DISABLED);
              repository.save(enrollment);
            });
    disableUserMfaFlagIfNoActiveEnrollments(userId);
  }

  private void disableUserMfaFlagIfNoActiveEnrollments(UUID userId) {
    if (repository.findByUserIdAndStatus(userId, MfaStatus.ACTIVE).isEmpty()) {
      userRepository
          .findById(userId)
          .ifPresent(
              user -> {
                user.setMfaEnabled(false);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
              });
    }
  }

  private boolean matches(MfaEnrollment enrollment, String code) {
    String base32Secret = fieldCipher.decrypt(enrollment.getSecretCiphertext());
    byte[] secretBytes = Base32.decode(base32Secret);
    return TotpAlgorithm.verify(
        secretBytes,
        code,
        TotpAlgorithm.DEFAULT_TIME_STEP_SECONDS,
        TotpAlgorithm.DEFAULT_DIGITS,
        ALLOWED_STEP_DRIFT);
  }
}

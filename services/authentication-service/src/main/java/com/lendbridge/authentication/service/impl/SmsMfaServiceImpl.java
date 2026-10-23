package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.service.SmsMfaService;

import com.lendbridge.authentication.dto.SmsVerifyRequest;
import com.lendbridge.authentication.model.MfaEnrollment;
import com.lendbridge.authentication.model.MfaMethod;
import com.lendbridge.authentication.model.MfaStatus;
import com.lendbridge.authentication.repository.MfaEnrollmentRepository;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.authentication.model.User;
import com.lendbridge.authentication.repository.UserJpaRepository;
import com.lendbridge.authentication.service.OtpService;
import com.lendbridge.authentication.model.OtpPurpose;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SmsMfaServiceImpl implements SmsMfaService {

  private final MfaEnrollmentRepository repository;
  private final UserJpaRepository userRepository;
  private final OtpService otpService;

  @Override
  @Transactional
  public void challenge(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    otpService.issue(userId, OtpPurpose.MFA_SMS, user.getPhone());
  }

  @Override
  @Transactional
  public void verify(UUID userId, SmsVerifyRequest request) {
    otpService.verify(userId, OtpPurpose.MFA_SMS, request.getCode());

    MfaEnrollment enrollment =
        repository
            .findByUserIdAndMethodAndStatus(userId, MfaMethod.SMS, MfaStatus.ACTIVE)
            .orElseGet(
                () ->
                    MfaEnrollment.builder()
                        .id(UuidV7.generate())
                        .userId(userId)
                        .method(MfaMethod.SMS)
                        .status(MfaStatus.PENDING)
                        .createdAt(Instant.now())
                        .build());
    enrollment.setStatus(MfaStatus.ACTIVE);
    if (enrollment.getActivatedAt() == null) {
      enrollment.setActivatedAt(Instant.now());
    }
    repository.save(enrollment);

    User user = userRepository.findById(userId).orElseThrow();
    if (!user.isMfaEnabled()) {
      user.setMfaEnabled(true);
      user.setUpdatedAt(Instant.now());
      userRepository.save(user);
    }
  }

  @Override
  @Transactional
  public void disable(UUID userId) {
    repository
        .findByUserIdAndMethodAndStatus(userId, MfaMethod.SMS, MfaStatus.ACTIVE)
        .ifPresent(
            enrollment -> {
              enrollment.setStatus(MfaStatus.DISABLED);
              repository.save(enrollment);
            });
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
}

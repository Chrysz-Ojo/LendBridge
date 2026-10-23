package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.service.RegistrationService;

import com.lendbridge.authentication.dto.RegisterRequest;
import com.lendbridge.authentication.dto.VerifyEmailRequest;
import com.lendbridge.authentication.dto.VerifyPhoneRequest;
import com.lendbridge.authentication.dto.RegisterResponse;
import com.lendbridge.authentication.model.ConsentRecord;
import com.lendbridge.authentication.model.ConsentType;
import com.lendbridge.authentication.model.EmailVerificationToken;
import com.lendbridge.authentication.repository.ConsentRecordRepository;
import com.lendbridge.authentication.repository.EmailVerificationTokenRepository;
import com.lendbridge.authentication.crypto.HmacPhoneHasher;
import com.lendbridge.authentication.crypto.OpaqueTokenGenerator;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.authentication.notification.EmailSender;
import com.lendbridge.authentication.outbox.KafkaTopics;
import com.lendbridge.authentication.outbox.OutboxEventWriter;
import com.lendbridge.authentication.outbox.UserRegisteredPayload;
import com.lendbridge.authentication.model.Email;
import com.lendbridge.authentication.model.Password;
import com.lendbridge.authentication.model.PhoneNumber;
import com.lendbridge.authentication.model.User;
import com.lendbridge.authentication.model.UserStatus;
import com.lendbridge.authentication.repository.UserJpaRepository;
import com.lendbridge.authentication.service.OtpService;
import com.lendbridge.authentication.model.OtpPurpose;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

  private static final String TERMS_VERSION = "2026-01-01";
  private static final String PRIVACY_VERSION = "2026-01-01";
  private static final long EMAIL_TOKEN_TTL_HOURS = 24;

  private final UserJpaRepository userRepository;
  private final ConsentRecordRepository consentRecordRepository;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final HmacPhoneHasher hmacPhoneHasher;
  private final OpaqueTokenGenerator opaqueTokenGenerator;
  private final OtpService otpService;
  private final EmailSender emailSender;
  private final OutboxEventWriter outboxEventWriter;

  @Value("${auth.registration.email-verification-base-url}")
  private String emailVerificationBaseUrl;

  @Override
  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    Email email = Email.of(request.getEmail());
    PhoneNumber phone = PhoneNumber.of(request.getPhone());
    Password password = Password.of(request.getPassword());

    if (!Boolean.TRUE.equals(request.getAcceptTerms())
        || !Boolean.TRUE.equals(request.getAcceptPrivacy())) {
      throw new BadRequestException(
          "CONSENT_REQUIRED", "Terms of service and privacy policy consent are both required");
    }

    String phoneHash = hmacPhoneHasher.hash(phone.getValue());
    if (userRepository.existsByEmail(email.getValue())) {
      throw new ConflictException("EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }
    if (userRepository.existsByPhoneHash(phoneHash)) {
      throw new ConflictException("PHONE_ALREADY_REGISTERED", "Phone number is already registered");
    }

    Instant now = Instant.now();
    User user =
        User.builder()
            .id(UuidV7.generate())
            .email(email.getValue())
            .phone(phone.getValue())
            .phoneHash(phoneHash)
            .passwordHash(passwordEncoder.encode(password.getValue()))
            .status(UserStatus.PENDING_VERIFICATION)
            .fullName(request.getFullName())
            .emailVerified(false)
            .phoneVerified(false)
            .mfaEnabled(false)
            .failedLoginAttempts(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
    userRepository.save(user);

    recordConsent(user.getId(), ConsentType.TERMS_OF_SERVICE, TERMS_VERSION, now);
    recordConsent(user.getId(), ConsentType.PRIVACY_POLICY, PRIVACY_VERSION, now);

    otpService.issue(user.getId(), OtpPurpose.PHONE_VERIFICATION, phone.getValue());

    String rawToken = opaqueTokenGenerator.generateRaw();
    EmailVerificationToken token =
        EmailVerificationToken.builder()
            .id(UuidV7.generate())
            .userId(user.getId())
            .tokenHash(opaqueTokenGenerator.hash(rawToken))
            .expiresAt(now.plus(EMAIL_TOKEN_TTL_HOURS, ChronoUnit.HOURS))
            .createdAt(now)
            .build();
    emailVerificationTokenRepository.save(token);
    emailSender.send(
        user.getEmail(),
        "Verify your LendBridge email",
        "Verify your email: " + emailVerificationBaseUrl + "?token=" + rawToken);

    return new RegisterResponse(user.getId(), user.getStatus().name());
  }

  @Override
  @Transactional
  public void verifyPhone(VerifyPhoneRequest request) {
    User user =
        userRepository
            .findById(request.getUserId())
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    otpService.verify(request.getUserId(), OtpPurpose.PHONE_VERIFICATION, request.getCode());
    user.setPhoneVerified(true);
    user.setUpdatedAt(Instant.now());
    activateIfFullyVerified(user);
    userRepository.save(user);
  }

  @Override
  @Transactional
  public void verifyEmail(VerifyEmailRequest request) {
    String hash = opaqueTokenGenerator.hash(request.getToken());
    EmailVerificationToken token =
        emailVerificationTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new BadRequestException("TOKEN_INVALID", "Invalid verification token"));
    if (token.isConsumed()) {
      throw new BadRequestException("TOKEN_ALREADY_USED", "Verification token already used");
    }
    if (token.isExpired(Instant.now())) {
      throw new BadRequestException("TOKEN_EXPIRED", "Verification token has expired");
    }
    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    token.setConsumedAt(Instant.now());
    emailVerificationTokenRepository.save(token);

    user.setEmailVerified(true);
    user.setUpdatedAt(Instant.now());
    activateIfFullyVerified(user);
    userRepository.save(user);
  }

  private void activateIfFullyVerified(User user) {
    if (user.isFullyVerified() && user.getStatus() != UserStatus.ACTIVE) {
      user.setStatus(UserStatus.ACTIVE);
      outboxEventWriter.write(
          "User",
          user.getId().toString(),
          KafkaTopics.USER_REGISTERED,
          new UserRegisteredPayload(
              user.getId().toString(),
              user.getEmail(),
              user.isEmailVerified(),
              user.isPhoneVerified(),
              Instant.now().toEpochMilli()));
    }
  }

  private void recordConsent(UUID userId, ConsentType type, String version, Instant now) {
    consentRecordRepository.save(
        ConsentRecord.builder()
            .id(UuidV7.generate())
            .userId(userId)
            .consentType(type)
            .version(version)
            .acceptedAt(now)
            .build());
  }
}

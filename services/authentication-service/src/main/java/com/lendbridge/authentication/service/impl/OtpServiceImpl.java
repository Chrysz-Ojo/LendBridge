package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.service.OtpService;

import com.lendbridge.authentication.crypto.OpaqueTokenGenerator;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.authentication.notification.SmsSender;
import com.lendbridge.authentication.model.OtpChallenge;
import com.lendbridge.authentication.model.OtpPurpose;
import com.lendbridge.authentication.repository.OtpChallengeRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

  private static final int CODE_LENGTH = 6;
  private static final long TTL_MINUTES = 5;
  private static final int MAX_ATTEMPTS = 5;

  private final OtpChallengeRepository repository;
  private final OpaqueTokenGenerator opaqueTokenGenerator;
  private final SmsSender smsSender;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public void issue(UUID userId, OtpPurpose purpose, String destinationPhone) {
    String code = generateNumericCode();
    OtpChallenge challenge =
        OtpChallenge.builder()
            .id(UuidV7.generate())
            .userId(userId)
            .purpose(purpose)
            .codeHash(opaqueTokenGenerator.hash(code))
            .expiresAt(Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES))
            .attempts(0)
            .maxAttempts(MAX_ATTEMPTS)
            .createdAt(Instant.now())
            .build();
    repository.save(challenge);
    smsSender.send(destinationPhone, "Your LendBridge verification code is " + code);
  }

  @Override
  @Transactional
  public void verify(UUID userId, OtpPurpose purpose, String code) {
    List<OtpChallenge> challenges =
        repository.findByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose);
    OtpChallenge latest =
        challenges.stream()
            .filter(c -> !c.isConsumed())
            .findFirst()
            .orElseThrow(() -> new BadRequestException("OTP_NOT_FOUND", "No active OTP challenge"));

    if (latest.isExpired(Instant.now())) {
      throw new BadRequestException("OTP_EXPIRED", "OTP challenge has expired");
    }
    if (latest.isExhausted()) {
      throw new BadRequestException("OTP_LOCKED", "Too many incorrect attempts");
    }
    if (!latest.getCodeHash().equals(opaqueTokenGenerator.hash(code))) {
      latest.setAttempts(latest.getAttempts() + 1);
      repository.save(latest);
      throw new BadRequestException("OTP_INCORRECT", "Incorrect OTP code");
    }
    latest.setConsumedAt(Instant.now());
    repository.save(latest);
  }

  private String generateNumericCode() {
    int bound = (int) Math.pow(10, CODE_LENGTH);
    int value = secureRandom.nextInt(bound);
    return String.format("%0" + CODE_LENGTH + "d", value);
  }
}

package com.lendbridge.authentication.service.impl;

import com.lendbridge.authentication.dto.ChangePinRequest;
import com.lendbridge.authentication.dto.OtpIssueRequest;
import com.lendbridge.authentication.dto.PinResetChallengeRequest;
import com.lendbridge.authentication.dto.PinResetVerifyRequest;
import com.lendbridge.authentication.dto.SetPinRequest;
import com.lendbridge.authentication.dto.VerifyPinRequest;
import com.lendbridge.authentication.exception.PinLockedException;
import com.lendbridge.authentication.model.OtpPurpose;
import com.lendbridge.authentication.model.PinAttempt;
import com.lendbridge.authentication.model.PinLockoutPolicy;
import com.lendbridge.authentication.model.TransactionPin;
import com.lendbridge.authentication.model.User;
import com.lendbridge.authentication.repository.PinAttemptRepository;
import com.lendbridge.authentication.repository.TransactionPinRepository;
import com.lendbridge.authentication.repository.UserJpaRepository;
import com.lendbridge.authentication.service.OtpService;
import com.lendbridge.authentication.service.PinService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PinServiceImpl implements PinService {

  private final TransactionPinRepository pinRepository;
  private final PinAttemptRepository pinAttemptRepository;
  private final UserJpaRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final OtpService otpService;

  @Override
  @Transactional
  public void setPin(UUID userId, SetPinRequest request) {
    validatePinFormat(request.getPin());
    if (pinRepository.existsById(userId)) {
      throw new BadRequestException("PIN_ALREADY_SET", "A transaction PIN is already set; use change instead");
    }
    Instant now = Instant.now();
    TransactionPin pin =
        TransactionPin.builder()
            .userId(userId)
            .pinHash(passwordEncoder.encode(request.getPin()))
            .failedAttempts(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
    pinRepository.save(pin);
  }


  @Override
  @Transactional
  public void changePin(UUID userId, ChangePinRequest request) {
    validatePinFormat(request.getNewPin());
    TransactionPin pin = findPinOrThrow(userId);
    checkNotLocked(pin);
    if (!passwordEncoder.matches(request.getCurrentPin(), pin.getPinHash())) {
      registerFailure(pin);
      throw new UnauthorizedException("PIN_INCORRECT", "Current PIN is incorrect");
    }
    pin.setPinHash(passwordEncoder.encode(request.getNewPin()));
    registerSuccess(pin);
  }

  @Override
  @Transactional
  public void requestPinReset(UUID userId, PinResetChallengeRequest request) {
    User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid credentials");
    }
    otpService.issue(
        userId,
        OtpIssueRequest.builder()
            .purpose(OtpPurpose.PIN_RESET)
            .destinationPhone(user.getPhone())
            .destinationEmail(user.getEmail())
            .build());
  }

  @Override
  @Transactional
  public void confirmPinReset(UUID userId, PinResetVerifyRequest request) {
    validatePinFormat(request.getNewPin());
    otpService.verify(userId, OtpPurpose.PIN_RESET, request.getCode());

    Instant now = Instant.now();
    TransactionPin pin =
        pinRepository
            .findByUserIdForUpdate(userId)
            .orElseGet(() -> TransactionPin.builder().userId(userId).failedAttempts(0).createdAt(now).build());
    pin.setPinHash(passwordEncoder.encode(request.getNewPin()));
    pin.setFailedAttempts(0);
    pin.setLockedUntil(null);
    pin.setUpdatedAt(now);
    pinRepository.save(pin);
    logAttempt(userId, true);
  }

  @Override
  @Transactional
  public void verifyPin(UUID userId, VerifyPinRequest request) {
    TransactionPin pin = findPinOrThrow(userId);
    checkNotLocked(pin);
    if (!passwordEncoder.matches(request.getPin(), pin.getPinHash())) {
      registerFailure(pin);
      throw new UnauthorizedException("PIN_INCORRECT", "Incorrect PIN");
    }
    registerSuccess(pin);
  }

  private void checkNotLocked(TransactionPin pin) {
    if (pin.isCurrentlyLocked(Instant.now())) {
      throw new PinLockedException("Transaction PIN is locked until " + pin.getLockedUntil());
    }
  }

  private void registerFailure(TransactionPin pin) {
    Instant now = Instant.now();
    pin.setFailedAttempts(pin.getFailedAttempts() + 1);
    Instant lockUntil = PinLockoutPolicy.lockedUntilAfterFailure(pin.getFailedAttempts(), now);
    if (lockUntil != null) {
      pin.setLockedUntil(lockUntil);
    }
    pin.setUpdatedAt(now);
    pinRepository.save(pin);
    logAttempt(pin.getUserId(), false);
  }

  private void registerSuccess(TransactionPin pin) {
    pin.setFailedAttempts(0);
    pin.setLockedUntil(null);
    pin.setUpdatedAt(Instant.now());
    pinRepository.save(pin);
    logAttempt(pin.getUserId(), true);
  }

  private void logAttempt(UUID userId, boolean success) {
    pinAttemptRepository.save(
        PinAttempt.builder().id(UuidV7.generate()).userId(userId).success(success).createdAt(Instant.now()).build());
  }

  private TransactionPin findPinOrThrow(UUID userId) {
    return pinRepository
        .findByUserIdForUpdate(userId)
        .orElseThrow(() -> new NotFoundException("PIN_NOT_SET", "No transaction PIN set for this user"));
  }

  private void validatePinFormat(String pin) {
    if (pin == null || !pin.matches("\\d{4,6}")) {
      throw new BadRequestException("PIN_INVALID_FORMAT", "PIN must be 4 to 6 digits");
    }
    if (isObviousPattern(pin)) {
      throw new BadRequestException("PIN_TOO_WEAK", "PIN is too easy to guess");
    }
  }

  private boolean isObviousPattern(String pin) {
    return isSequential(pin, 1) || isSequential(pin, -1) || isRepeatingBlock(pin);
  }

  /** Rejects a PIN made of a short block repeated to fill the length — e.g. "1111" (block "1"),
   * "1212"/"3434" (repeated pairs, block "12"), "123123" (repeated triplet, block "123"). Checking
   * every divisor of the PIN's length, not just a single-digit repeat, is what catches repeated
   * pairs and repeating sequences, not only "all one digit". */
  private boolean isRepeatingBlock(String pin) {
    int length = pin.length();
    for (int blockSize = 1; blockSize <= length / 2; blockSize++) {
      if (length % blockSize != 0) {
        continue;
      }
      String block = pin.substring(0, blockSize);
      boolean matchesWholeLength = true;
      for (int i = blockSize; i < length; i += blockSize) {
        if (!pin.regionMatches(i, block, 0, blockSize)) {
          matchesWholeLength = false;
          break;
        }
      }
      if (matchesWholeLength) {
        return true;
      }
    }
    return false;
  }

  private boolean isSequential(String pin, int step) {
    for (int i = 1; i < pin.length(); i++) {
      int previous = pin.charAt(i - 1) - '0';
      int current = pin.charAt(i) - '0';
      if (current - previous != step) {
        return false;
      }
    }
    return true;
  }
}

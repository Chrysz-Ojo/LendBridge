package com.lendbridge.kyc.service;

import com.lendbridge.kyc.dto.StartVerificationRequest;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import java.util.UUID;

public interface VerificationService {

  VerificationStatusResponse startVerification(UUID userId, StartVerificationRequest request);

  VerificationStatusResponse getStatus(UUID userId);
}

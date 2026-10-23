package com.lendbridge.authentication.service;

import com.lendbridge.authentication.dto.TotpVerifyRequest;
import com.lendbridge.authentication.dto.TotpEnrollResponse;
import java.util.UUID;

public interface TotpService {

  TotpEnrollResponse enroll(UUID userId);

  void verifyAndActivate(UUID userId, TotpVerifyRequest request);

  boolean verifyActive(UUID userId, String code);

  void disable(UUID userId);
}

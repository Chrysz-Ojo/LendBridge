package com.lendbridge.kyc.provider;

import com.lendbridge.kyc.model.ProviderName;

/**
 * Port behind which every KYC/identity verification vendor integration sits, per CLAUDE.md's
 * external-provider rule — a fake implementation is what every test and CI run exercises; the real
 * adapters ({@code SmileIdentityVerificationProvider}, {@code DojahVerificationProvider},
 * {@code PremblyVerificationProvider}) are production code for whoever has real sandbox
 * credentials, never called by anything automated in this repo (mirrors payment-service's
 * {@code PaymentProvider}/ADR 0010 exactly).
 */
public interface VerificationProvider {

  ProviderName name();

  ProviderCheckResult checkBvn(BvnCheckRequest request);

  ProviderCheckResult checkNin(NinCheckRequest request);

  ProviderCheckResult checkLiveness(LivenessCheckRequest request);
}

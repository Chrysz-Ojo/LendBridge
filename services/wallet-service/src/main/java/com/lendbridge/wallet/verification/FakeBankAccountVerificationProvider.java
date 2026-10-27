package com.lendbridge.wallet.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fake verifier for local development and tests; see {@code FakeVirusScanner} in document-service
 * for the same pattern (CLAUDE.md's external-provider rule). There is no KYC-service yet, so there
 * is no trusted name to cross-check against — {@code accountHolderName} here is whatever the caller
 * supplied in the request, not a verified KYC record. This is a named v1 gap (see ADR), not a real
 * "name matches KYC" check. Deterministic, EICAR-style: a sentinel deny-list prefix always fails,
 * exactly as a real resolver would reject a known-bad test account number; everything else passes.
 */
@Component
public class FakeBankAccountVerificationProvider implements BankAccountVerificationProvider {

  private static final Logger log =
      LoggerFactory.getLogger(FakeBankAccountVerificationProvider.class);
  private static final String DENY_LISTED_PREFIX = "0000000";

  @Override
  public BankAccountVerificationResult verify(
      String accountNumber, String bankCode, String accountHolderName) {
    if (accountNumber.startsWith(DENY_LISTED_PREFIX)) {
      log.warn(
          "Fake bank account verification: REJECTED (deny-listed test account {})", accountNumber);
      return new BankAccountVerificationResult(false, null, "Account could not be verified");
    }
    log.info("Fake bank account verification of {} at {}: VERIFIED", accountNumber, bankCode);
    return new BankAccountVerificationResult(true, accountHolderName, null);
  }
}

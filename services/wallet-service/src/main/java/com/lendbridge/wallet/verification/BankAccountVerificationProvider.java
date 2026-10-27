package com.lendbridge.wallet.verification;

/** Port for resolving/verifying a bank account against its issuing bank. A real adapter (e.g. a
 * Paystack-style account-resolution API) can implement this later behind the same interface, per
 * CLAUDE.md's external-provider rule. */
public interface BankAccountVerificationProvider {

  BankAccountVerificationResult verify(String accountNumber, String bankCode, String accountHolderName);
}

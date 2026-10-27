package com.lendbridge.wallet.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FakeBankAccountVerificationProviderTest {

  private final FakeBankAccountVerificationProvider provider =
      new FakeBankAccountVerificationProvider();

  @Test
  void verify_echoesSuppliedName_whenNotDenyListed() {
    BankAccountVerificationResult result =
        provider.verify("1234567890", "058", "Caller Supplied Name");

    assertThat(result.isVerified()).isTrue();
    assertThat(result.getResolvedAccountName()).isEqualTo("Caller Supplied Name");
  }

  @Test
  void verify_rejectsDenyListedPrefix() {
    BankAccountVerificationResult result = provider.verify("0000000001", "058", "Someone");

    assertThat(result.isVerified()).isFalse();
    assertThat(result.getResolvedAccountName()).isNull();
  }
}

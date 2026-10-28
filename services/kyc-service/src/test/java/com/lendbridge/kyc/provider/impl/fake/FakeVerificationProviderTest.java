package com.lendbridge.kyc.provider.impl.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.provider.BvnCheckRequest;
import com.lendbridge.kyc.provider.LivenessCheckRequest;
import com.lendbridge.kyc.provider.NinCheckRequest;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeVerificationProviderTest {

  private final FakeVerificationProvider provider = new FakeVerificationProvider();

  @Test
  void name_returnsFake() {
    assertThat(provider.name()).isEqualTo(ProviderName.FAKE);
  }

  @Test
  void checkBvn_passesForAnOrdinaryBvn() {
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest("12345678901", "Jane Doe", LocalDate.of(1990, 1, 1)));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
    assertThat(result.getNameMatch()).isTrue();
    assertThat(result.getDobMatch()).isTrue();
  }

  @Test
  void checkBvn_mismatchesForTheMismatchSentinel() {
    String bvn = FakeVerificationProvider.mismatchSentinel("12345678901");
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest(bvn, "Jane Doe", LocalDate.of(1990, 1, 1)));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
    assertThat(result.getNameMatch()).isFalse();
  }

  @Test
  void checkBvn_failsForTheFailSentinel() {
    String bvn = FakeVerificationProvider.failSentinel("12345678901");
    ProviderCheckResult result = provider.checkBvn(new BvnCheckRequest(bvn, "Jane Doe", LocalDate.of(1990, 1, 1)));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
  }

  @Test
  void checkNin_passesForAnOrdinaryNin() {
    ProviderCheckResult result = provider.checkNin(new NinCheckRequest("98765432109", "Jane Doe", LocalDate.of(1990, 1, 1)));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
  }

  @Test
  void checkNin_failsForTheFailSentinel() {
    String nin = FakeVerificationProvider.failSentinel("98765432109");
    ProviderCheckResult result = provider.checkNin(new NinCheckRequest(nin, "Jane Doe", LocalDate.of(1990, 1, 1)));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
  }

  @Test
  void checkLiveness_passesWhenNoSentinelHintIsPresent() {
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), "id.jpg"));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.PASSED);
    assertThat(result.getLivenessPassed()).isTrue();
    assertThat(result.getFaceMatchPassed()).isTrue();
  }

  @Test
  void checkLiveness_mismatchesWhenTheSelfieFilenameCarriesTheMismatchSentinel() {
    String selfieHint = FakeVerificationProvider.mismatchSentinel("selfie.jpg");
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), selfieHint, UUID.randomUUID(), "id.jpg"));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.MISMATCH);
    assertThat(result.getLivenessPassed()).isTrue();
    assertThat(result.getFaceMatchPassed()).isFalse();
  }

  @Test
  void checkLiveness_failsWhenTheIdCardFilenameCarriesTheFailSentinel() {
    String idHint = FakeVerificationProvider.failSentinel("id.jpg");
    ProviderCheckResult result =
        provider.checkLiveness(new LivenessCheckRequest(UUID.randomUUID(), "selfie.jpg", UUID.randomUUID(), idHint));
    assertThat(result.getOutcome()).isEqualTo(CheckOutcome.FAILED);
    assertThat(result.getLivenessPassed()).isFalse();
    assertThat(result.getFaceMatchPassed()).isFalse();
  }
}

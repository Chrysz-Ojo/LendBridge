package com.lendbridge.payment.provider.impl.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.provider.ChargeRequest;
import com.lendbridge.payment.provider.PayoutRequest;
import com.lendbridge.payment.provider.ProviderOperationStatus;
import com.lendbridge.payment.provider.ProviderWebhookEvent;
import com.lendbridge.payment.provider.ProviderWebhookEventType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FakePaymentProviderTest {

  private final FakePaymentProvider provider = new FakePaymentProvider();

  @Test
  void initiateCharge_succeedsForOrdinaryReference() {
    var result = provider.initiateCharge(new ChargeRequest("ref-1", 5000, "NGN", "a@b.com", "idem-1"));
    assertThat(result.getStatus()).isEqualTo(ProviderOperationStatus.SUCCESSFUL);
  }

  @Test
  void initiateCharge_failsForSentinelReference() {
    String reference = FakePaymentProvider.failSentinelReference("ref-2");
    var result = provider.initiateCharge(new ChargeRequest(reference, 5000, "NGN", "a@b.com", "idem-2"));
    assertThat(result.getStatus()).isEqualTo(ProviderOperationStatus.FAILED);
  }

  @Test
  void initiatePayout_failsForSentinelReference() {
    String reference = FakePaymentProvider.failSentinelReference("payout-1");
    var result =
        provider.initiatePayout(
            new PayoutRequest(reference, 5000, "NGN", "058", "0123456789", "Jane Doe", "idem-3"));
    assertThat(result.getStatus()).isEqualTo(ProviderOperationStatus.FAILED);
  }

  @Test
  void verifySignature_acceptsOnlyTheValidHeader() {
    assertThat(provider.verifySignature(new byte[0], FakePaymentProvider.validSignatureHeader())).isTrue();
    assertThat(provider.verifySignature(new byte[0], Map.of("x-fake-signature", "wrong"))).isFalse();
    assertThat(provider.verifySignature(new byte[0], Map.of())).isFalse();
  }

  @Test
  void parseWebhookEvent_roundTripsAConstructedBody() {
    String body = FakePaymentProvider.buildWebhookBody(ProviderWebhookEventType.CHARGE_SUCCEEDED, "ref-9", 7500);
    ProviderWebhookEvent event = provider.parseWebhookEvent(body.getBytes(), Map.of());

    assertThat(event.getProvider()).isEqualTo(ProviderName.FAKE);
    assertThat(event.getEventType()).isEqualTo(ProviderWebhookEventType.CHARGE_SUCCEEDED);
    assertThat(event.getProviderReference()).isEqualTo("ref-9");
    assertThat(event.getAmountKobo()).isEqualTo(7500);
  }

  @Test
  void parseWebhookEvent_returnsUnknownForMalformedBody() {
    ProviderWebhookEvent event = provider.parseWebhookEvent("not json".getBytes(), Map.of());
    assertThat(event.getEventType()).isEqualTo(ProviderWebhookEventType.UNKNOWN);
  }

  @Test
  void generateSettlementReport_returnsEmptyList() {
    assertThat(provider.generateSettlementReport(ProviderName.FAKE, java.time.Instant.EPOCH, java.time.Instant.now()))
        .isEmpty();
  }
}

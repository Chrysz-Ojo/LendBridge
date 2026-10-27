package com.lendbridge.payment.provider.impl.flutterwave;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.payment.provider.ProviderWebhookEvent;
import com.lendbridge.payment.provider.ProviderWebhookEventType;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code verif-hash} is a static shared-secret string compare, not HMAC — confirmed directly
 * from Flutterwave's docs (see {@link FlutterwavePaymentProvider}'s class javadoc); these tests
 * exercise exactly that comparison, plus webhook event mapping. No live network call. */
class FlutterwavePaymentProviderTest {

  private static final String VERIF_HASH = "configured-dashboard-secret";

  private final FlutterwavePaymentProvider provider = new FlutterwavePaymentProvider(null, VERIF_HASH);

  @Test
  void verifySignature_acceptsTheConfiguredSharedSecret() {
    assertThat(provider.verifySignature(new byte[0], Map.of("verif-hash", VERIF_HASH))).isTrue();
  }

  @Test
  void verifySignature_rejectsAnyOtherValue() {
    assertThat(provider.verifySignature(new byte[0], Map.of("verif-hash", "not-the-secret"))).isFalse();
  }

  @Test
  void verifySignature_rejectsAMissingHeader() {
    assertThat(provider.verifySignature(new byte[0], Map.of())).isFalse();
  }

  @Test
  void parseWebhookEvent_mapsSuccessfulCharge() {
    String body =
        "{\"event\":\"charge.completed\",\"data\":{\"status\":\"successful\",\"tx_ref\":\"ref-1\",\"amount\":2000}}";
    ProviderWebhookEvent event = provider.parseWebhookEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

    assertThat(event.getEventType()).isEqualTo(ProviderWebhookEventType.CHARGE_SUCCEEDED);
    assertThat(event.getProviderReference()).isEqualTo("ref-1");
  }

  @Test
  void parseWebhookEvent_mapsFailedCharge() {
    String body = "{\"event\":\"charge.completed\",\"data\":{\"status\":\"failed\",\"tx_ref\":\"ref-2\"}}";
    assertThat(provider.parseWebhookEvent(body.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.CHARGE_FAILED);
  }

  @Test
  void parseWebhookEvent_mapsTransferOutcomes() {
    String successful = "{\"event\":\"transfer.completed\",\"data\":{\"status\":\"SUCCESSFUL\",\"reference\":\"r1\"}}";
    String failed = "{\"event\":\"transfer.completed\",\"data\":{\"status\":\"FAILED\",\"reference\":\"r2\"}}";

    assertThat(provider.parseWebhookEvent(successful.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.TRANSFER_SUCCESSFUL);
    assertThat(provider.parseWebhookEvent(failed.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.TRANSFER_FAILED);
  }
}

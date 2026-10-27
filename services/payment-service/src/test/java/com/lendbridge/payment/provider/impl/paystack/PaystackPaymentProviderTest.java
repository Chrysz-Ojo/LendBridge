package com.lendbridge.payment.provider.impl.paystack;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.payment.provider.ProviderWebhookEvent;
import com.lendbridge.payment.provider.ProviderWebhookEventType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** No live network call — only the signature/webhook-parsing logic, which doesn't touch the
 * Feign client, is exercised here (see the approved plan's Tests section). */
class PaystackPaymentProviderTest {

  private static final String SECRET_KEY = "sk_test_sample_secret";

  private final PaystackPaymentProvider provider = new PaystackPaymentProvider(null, SECRET_KEY);

  @Test
  void verifySignature_acceptsACorrectlyComputedHmac() throws Exception {
    byte[] body = "{\"event\":\"charge.success\"}".getBytes(StandardCharsets.UTF_8);
    String signature = computeHmacSha512Hex(SECRET_KEY, body);

    assertThat(provider.verifySignature(body, Map.of("x-paystack-signature", signature))).isTrue();
  }

  @Test
  void verifySignature_rejectsAWrongSignature() {
    byte[] body = "{\"event\":\"charge.success\"}".getBytes(StandardCharsets.UTF_8);
    assertThat(provider.verifySignature(body, Map.of("x-paystack-signature", "wrong"))).isFalse();
  }

  @Test
  void verifySignature_rejectsAMissingHeader() {
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    assertThat(provider.verifySignature(body, Map.of())).isFalse();
  }

  @Test
  void parseWebhookEvent_mapsChargeSuccess() {
    String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ref-1\",\"amount\":5000}}";
    ProviderWebhookEvent event = provider.parseWebhookEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

    assertThat(event.getEventType()).isEqualTo(ProviderWebhookEventType.CHARGE_SUCCEEDED);
    assertThat(event.getProviderReference()).isEqualTo("ref-1");
    assertThat(event.getAmountKobo()).isEqualTo(5000);
  }

  @Test
  void parseWebhookEvent_mapsTransferFailedAndReversed() {
    String failed = "{\"event\":\"transfer.failed\",\"data\":{\"transfer_code\":\"tc-1\"}}";
    String reversed = "{\"event\":\"transfer.reversed\",\"data\":{\"transfer_code\":\"tc-2\"}}";

    assertThat(provider.parseWebhookEvent(failed.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.TRANSFER_FAILED);
    assertThat(provider.parseWebhookEvent(reversed.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.TRANSFER_FAILED);
  }

  @Test
  void parseWebhookEvent_returnsUnknownForUnrecognizedEvent() {
    String body = "{\"event\":\"customeridentification.failed\",\"data\":{}}";
    assertThat(provider.parseWebhookEvent(body.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.UNKNOWN);
  }

  private static String computeHmacSha512Hex(String key, byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    return HexFormat.of().formatHex(mac.doFinal(body));
  }
}

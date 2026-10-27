package com.lendbridge.payment.provider.impl.monnify;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.payment.provider.ProviderWebhookEvent;
import com.lendbridge.payment.provider.ProviderWebhookEventType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** No live network call — only signature verification (including the sandbox-vs-production
 * absent-header tolerance confirmed from Monnify's docs) and webhook-event mapping. */
class MonnifyPaymentProviderTest {

  private static final String CLIENT_SECRET = "sandbox-client-secret";

  private MonnifyPaymentProvider sandboxProvider() {
    return new MonnifyPaymentProvider(null, CLIENT_SECRET, "CONTRACT1", "https://sandbox.monnify.com");
  }

  private MonnifyPaymentProvider productionProvider() {
    return new MonnifyPaymentProvider(null, CLIENT_SECRET, "CONTRACT1", "https://api.monnify.com");
  }

  @Test
  void verifySignature_acceptsACorrectlyComputedHmac() throws Exception {
    byte[] body = "{\"eventType\":\"SUCCESSFUL_TRANSACTION\"}".getBytes(StandardCharsets.UTF_8);
    String signature = computeHmacSha512Hex(CLIENT_SECRET, body);

    assertThat(sandboxProvider().verifySignature(body, Map.of("monnify-signature", signature))).isTrue();
  }

  @Test
  void verifySignature_toleratesMissingHeaderInSandbox() {
    assertThat(sandboxProvider().verifySignature(new byte[0], Map.of())).isTrue();
  }

  @Test
  void verifySignature_rejectsMissingHeaderInProduction() {
    assertThat(productionProvider().verifySignature(new byte[0], Map.of())).isFalse();
  }

  @Test
  void verifySignature_rejectsAWrongSignatureEvenInSandbox() {
    assertThat(sandboxProvider().verifySignature(new byte[0], Map.of("monnify-signature", "wrong"))).isFalse();
  }

  @Test
  void parseWebhookEvent_mapsPaidTransactionAsChargeSucceeded() {
    String body =
        "{\"eventType\":\"SUCCESSFUL_TRANSACTION\",\"eventData\":{\"paymentStatus\":\"PAID\",\"transactionReference\":\"tr-1\",\"amountPaid\":3000}}";
    ProviderWebhookEvent event = sandboxProvider().parseWebhookEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

    assertThat(event.getEventType()).isEqualTo(ProviderWebhookEventType.CHARGE_SUCCEEDED);
    assertThat(event.getProviderReference()).isEqualTo("tr-1");
    assertThat(event.getAmountKobo()).isEqualTo(3000);
  }

  @Test
  void parseWebhookEvent_mapsDisbursementOutcomes() {
    String successful = "{\"eventType\":\"SUCCESSFUL_DISBURSEMENT\",\"eventData\":{\"reference\":\"d-1\"}}";
    String failed = "{\"eventType\":\"FAILED_DISBURSEMENT\",\"eventData\":{\"reference\":\"d-2\"}}";
    String reversed = "{\"eventType\":\"REVERSED_DISBURSEMENT\",\"eventData\":{\"reference\":\"d-3\"}}";

    assertThat(sandboxProvider().parseWebhookEvent(successful.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.TRANSFER_SUCCESSFUL);
    assertThat(sandboxProvider().parseWebhookEvent(failed.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.TRANSFER_FAILED);
    assertThat(sandboxProvider().parseWebhookEvent(reversed.getBytes(StandardCharsets.UTF_8), Map.of()).getEventType())
        .isEqualTo(ProviderWebhookEventType.TRANSFER_FAILED);
  }

  private static String computeHmacSha512Hex(String key, byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    return HexFormat.of().formatHex(mac.doFinal(body));
  }
}

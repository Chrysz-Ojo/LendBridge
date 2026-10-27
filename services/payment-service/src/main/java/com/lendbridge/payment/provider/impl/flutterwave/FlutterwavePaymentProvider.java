package com.lendbridge.payment.provider.impl.flutterwave;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.provider.ChargeRequest;
import com.lendbridge.payment.provider.PayoutRequest;
import com.lendbridge.payment.provider.PaymentProvider;
import com.lendbridge.payment.provider.ProviderChargeResult;
import com.lendbridge.payment.provider.ProviderOperationStatus;
import com.lendbridge.payment.provider.ProviderPayoutResult;
import com.lendbridge.payment.provider.ProviderVirtualAccountResult;
import com.lendbridge.payment.provider.ProviderWebhookEvent;
import com.lendbridge.payment.provider.ProviderWebhookEventType;
import com.lendbridge.payment.provider.VirtualAccountRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real adapter against Flutterwave's v3 API. Its webhook "signature" ({@code verif-hash}) is a
 * static shared secret you configure in their dashboard, just echoed back and string-compared —
 * not HMAC, no real body-tamper protection — confirmed via direct review of Flutterwave's current
 * docs, which say so explicitly. Never invoked by anything automated in this repo.
 */
@Component
public class FlutterwavePaymentProvider implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(FlutterwavePaymentProvider.class);
  private static final String SIGNATURE_HEADER = "verif-hash";

  private final FlutterwaveClient client;
  private final String verifHash;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public FlutterwavePaymentProvider(FlutterwaveClient client, @Value("${flutterwave.verif-hash}") String verifHash) {
    this.client = client;
    this.verifHash = verifHash;
  }

  @Override
  public ProviderName name() {
    return ProviderName.FLUTTERWAVE;
  }

  @Override
  public ProviderChargeResult initiateCharge(ChargeRequest request) {
    // tx_ref is Flutterwave's own idempotency mechanism (confirmed: a repeated tx_ref is treated
    // as the same transaction, not a new charge) — our idempotency key passes straight through.
    JsonNode response =
        client.initiatePayment(
            Map.of(
                "tx_ref", request.getIdempotencyKey(),
                "amount", request.getAmountKobo(),
                "currency", request.getCurrency(),
                "customer", Map.of("email", request.getCustomerEmail())));
    JsonNode data = response.path("data");
    return ProviderChargeResult.builder()
        .providerReference(request.getIdempotencyKey())
        .status(ProviderOperationStatus.PENDING)
        .checkoutUrl(data.path("link").asText(null))
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderChargeResult verifyCharge(String providerReference) {
    JsonNode response = client.verifyByReference(providerReference);
    JsonNode data = response.path("data");
    String status = data.path("status").asText("");
    return ProviderChargeResult.builder()
        .providerReference(providerReference)
        .status(mapStatus(status))
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderVirtualAccountResult provisionVirtualAccount(VirtualAccountRequest request) {
    // Static virtual accounts require a BVN or NIN up front — gated on KYC data this repo
    // doesn't have yet (KYC-service doesn't exist); callers pass bvnOrNin=null until it does,
    // and this call will fail against the real API until then. That's expected, not a bug here.
    JsonNode response =
        client.createVirtualAccount(
            Map.of(
                "email", request.getCustomerEmail(),
                "is_permanent", true,
                "bvn", request.getBvnOrNin() == null ? "" : request.getBvnOrNin(),
                "tx_ref", request.getWalletId(),
                "currency", "NGN",
                "firstname", request.getCustomerName(),
                "lastname", ""));
    JsonNode data = response.path("data");
    return ProviderVirtualAccountResult.builder()
        .accountNumber(data.path("account_number").asText())
        .bankName(data.path("bank_name").asText())
        .providerReference(data.path("flw_ref").asText())
        .build();
  }

  @Override
  public ProviderPayoutResult initiatePayout(PayoutRequest request) {
    JsonNode response =
        client.initiateTransfer(
            Map.of(
                "account_bank", request.getBankCode(),
                "account_number", request.getAccountNumber(),
                "amount", request.getAmountKobo(),
                "currency", request.getCurrency(),
                "reference", request.getIdempotencyKey(),
                "narration", "LendBridge withdrawal"));
    JsonNode data = response.path("data");
    return ProviderPayoutResult.builder()
        .providerReference(String.valueOf(data.path("id").asLong()))
        .status(mapStatus(data.path("status").asText("")))
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
    String provided = headers.get(SIGNATURE_HEADER);
    if (provided == null) {
      return false;
    }
    return MessageDigest.isEqual(
        verifHash.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public ProviderWebhookEvent parseWebhookEvent(byte[] rawBody, Map<String, String> headers) {
    try {
      JsonNode node = objectMapper.readTree(rawBody);
      String event = node.path("event").asText("");
      JsonNode data = node.path("data");
      ProviderWebhookEventType eventType =
          switch (event) {
            case "charge.completed" ->
                "successful".equals(data.path("status").asText(""))
                    ? ProviderWebhookEventType.CHARGE_SUCCEEDED
                    : ProviderWebhookEventType.CHARGE_FAILED;
            case "transfer.completed" ->
                "SUCCESSFUL".equals(data.path("status").asText(""))
                    ? ProviderWebhookEventType.TRANSFER_SUCCESSFUL
                    : ProviderWebhookEventType.TRANSFER_FAILED;
            default -> ProviderWebhookEventType.UNKNOWN;
          };
      return ProviderWebhookEvent.builder()
          .provider(ProviderName.FLUTTERWAVE)
          .eventType(eventType)
          .providerReference(data.path("tx_ref").asText(data.path("reference").asText(null)))
          .amountKobo(data.path("amount").asLong(0))
          .rawPayloadJson(node.toString())
          .build();
    } catch (Exception e) {
      log.warn("Failed to parse Flutterwave webhook payload", e);
      return ProviderWebhookEvent.builder()
          .provider(ProviderName.FLUTTERWAVE)
          .eventType(ProviderWebhookEventType.UNKNOWN)
          .build();
    }
  }

  private ProviderOperationStatus mapStatus(String flutterwaveStatus) {
    return switch (flutterwaveStatus) {
      case "successful", "SUCCESSFUL" -> ProviderOperationStatus.SUCCESSFUL;
      case "failed", "FAILED" -> ProviderOperationStatus.FAILED;
      default -> ProviderOperationStatus.PENDING;
    };
  }
}

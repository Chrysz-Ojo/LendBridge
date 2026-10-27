package com.lendbridge.payment.provider.impl.paystack;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real adapter against Paystack's test/live API (same base URL, mode determined entirely by the
 * configured secret key's prefix) — confirmed via direct review of Paystack's current API docs
 * this session. Never invoked by anything automated in this repo; exercised only by its own unit
 * tests against fixed sample payloads, never a live network call.
 */
@Component
public class PaystackPaymentProvider implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(PaystackPaymentProvider.class);
  private static final String SIGNATURE_HEADER = "x-paystack-signature";
  private static final String HMAC_ALGORITHM = "HmacSHA512";

  private final PaystackClient client;
  private final String secretKey;

  public PaystackPaymentProvider(PaystackClient client, @Value("${paystack.secret-key}") String secretKey) {
    this.client = client;
    this.secretKey = secretKey;
  }

  @Override
  public ProviderName name() {
    return ProviderName.PAYSTACK;
  }

  @Override
  public ProviderChargeResult initiateCharge(ChargeRequest request) {
    // Paystack rejects a duplicate reference outright — its own idempotency mechanism, confirmed
    // directly from their docs — so we pass our own idempotency key straight through as theirs.
    JsonNode response =
        client.initializeTransaction(
            Map.of(
                "email", request.getCustomerEmail(),
                "amount", request.getAmountKobo(),
                "currency", request.getCurrency(),
                "reference", request.getIdempotencyKey()));
    JsonNode data = response.path("data");
    return ProviderChargeResult.builder()
        .providerReference(data.path("reference").asText(request.getIdempotencyKey()))
        .status(ProviderOperationStatus.PENDING)
        .checkoutUrl(data.path("authorization_url").asText(null))
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderChargeResult verifyCharge(String providerReference) {
    JsonNode response = client.verifyTransaction(providerReference);
    JsonNode data = response.path("data");
    String status = data.path("status").asText("");
    return ProviderChargeResult.builder()
        .providerReference(providerReference)
        .status(mapChargeStatus(status))
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderVirtualAccountResult provisionVirtualAccount(VirtualAccountRequest request) {
    JsonNode customerResponse =
        client.createCustomer(
            Map.of(
                "email", request.getCustomerEmail(),
                "first_name", request.getCustomerName(),
                "last_name", ""));
    String customerCode = customerResponse.path("data").path("customer_code").asText();

    JsonNode accountResponse =
        client.createDedicatedAccount(Map.of("customer", customerCode, "preferred_bank", "wema-bank"));
    JsonNode data = accountResponse.path("data");
    return ProviderVirtualAccountResult.builder()
        .accountNumber(data.path("account_number").asText())
        .bankName(data.path("bank").path("name").asText())
        .providerReference(customerCode)
        .build();
  }

  @Override
  public ProviderPayoutResult initiatePayout(PayoutRequest request) {
    JsonNode recipientResponse =
        client.createTransferRecipient(
            Map.of(
                "type", "nuban",
                "name", request.getAccountHolderName(),
                "account_number", request.getAccountNumber(),
                "bank_code", request.getBankCode()));
    String recipientCode = recipientResponse.path("data").path("recipient_code").asText();

    JsonNode transferResponse =
        client.initiateTransfer(
            Map.of(
                "source", "balance",
                "amount", request.getAmountKobo(),
                "recipient", recipientCode,
                "reference", request.getIdempotencyKey()));
    JsonNode data = transferResponse.path("data");
    return ProviderPayoutResult.builder()
        .providerReference(data.path("transfer_code").asText(request.getIdempotencyKey()))
        .status(mapTransferStatus(data.path("status").asText("")))
        .rawResponseJson(transferResponse.toString())
        .build();
  }

  @Override
  public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
    String providedSignature = headers.get(SIGNATURE_HEADER);
    if (providedSignature == null) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] computed = mac.doFinal(rawBody);
      String computedHex = HexFormat.of().formatHex(computed);
      return MessageDigest.isEqual(
          computedHex.getBytes(StandardCharsets.UTF_8), providedSignature.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      log.error("Failed to verify Paystack webhook signature", e);
      return false;
    }
  }

  @Override
  public ProviderWebhookEvent parseWebhookEvent(byte[] rawBody, Map<String, String> headers) {
    try {
      JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawBody);
      String event = node.path("event").asText("");
      JsonNode data = node.path("data");
      ProviderWebhookEventType eventType =
          switch (event) {
            case "charge.success" -> ProviderWebhookEventType.CHARGE_SUCCEEDED;
            case "transfer.success" -> ProviderWebhookEventType.TRANSFER_SUCCESSFUL;
            case "transfer.failed", "transfer.reversed" -> ProviderWebhookEventType.TRANSFER_FAILED;
            default -> ProviderWebhookEventType.UNKNOWN;
          };
      return ProviderWebhookEvent.builder()
          .provider(ProviderName.PAYSTACK)
          .eventType(eventType)
          .providerReference(data.path("reference").asText(data.path("transfer_code").asText(null)))
          .amountKobo(data.path("amount").asLong(0))
          .rawPayloadJson(node.toString())
          .build();
    } catch (Exception e) {
      log.warn("Failed to parse Paystack webhook payload", e);
      return ProviderWebhookEvent.builder()
          .provider(ProviderName.PAYSTACK)
          .eventType(ProviderWebhookEventType.UNKNOWN)
          .build();
    }
  }

  private ProviderOperationStatus mapChargeStatus(String paystackStatus) {
    return switch (paystackStatus) {
      case "success" -> ProviderOperationStatus.SUCCESSFUL;
      case "failed", "abandoned" -> ProviderOperationStatus.FAILED;
      default -> ProviderOperationStatus.PENDING;
    };
  }

  private ProviderOperationStatus mapTransferStatus(String paystackStatus) {
    return switch (paystackStatus) {
      case "success" -> ProviderOperationStatus.SUCCESSFUL;
      case "failed", "reversed" -> ProviderOperationStatus.FAILED;
      default -> ProviderOperationStatus.PENDING;
    };
  }
}

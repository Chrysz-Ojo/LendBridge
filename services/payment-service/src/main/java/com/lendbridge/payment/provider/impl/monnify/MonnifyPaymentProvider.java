package com.lendbridge.payment.provider.impl.monnify;

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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real adapter against Monnify. Two things confirmed via direct review of Monnify's current docs
 * that shape this class:
 *
 * <p><b>Payouts require a merchant-level OTP</b> — after {@code initiatePayout}, Monnify emails a
 * one-time code to the *merchant's own registered email* (not the end customer) and the transfer
 * sits at {@code PENDING_AUTHORIZATION} until someone submits that code via a separate
 * validate-otp call. That is fundamentally a human-in-the-loop step this adapter cannot automate
 * — {@link #initiatePayout} returns {@code PENDING} and stays there; completing a Monnify payout
 * requires a human to read the merchant inbox and call {@link MonnifyClient#validateDisbursementOtp}
 * out of band. This is a real operational cost of choosing Monnify for payouts specifically, not a
 * gap in this adapter.
 *
 * <p><b>The {@code monnify-signature} webhook header may be absent in sandbox</b> — verified when
 * present; absence is tolerated outside a production environment, not silently accepted in one.
 */
@Component
public class MonnifyPaymentProvider implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(MonnifyPaymentProvider.class);
  private static final String SIGNATURE_HEADER = "monnify-signature";
  private static final String HMAC_ALGORITHM = "HmacSHA512";

  private final MonnifyClient client;
  private final String clientSecret;
  private final String contractCode;
  private final boolean productionEnvironment;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public MonnifyPaymentProvider(
      MonnifyClient client,
      @Value("${monnify.client-secret}") String clientSecret,
      @Value("${monnify.contract-code}") String contractCode,
      @Value("${monnify.base-url}") String baseUrl) {
    this.client = client;
    this.clientSecret = clientSecret;
    this.contractCode = contractCode;
    this.productionEnvironment = baseUrl.contains("api.monnify.com");
  }

  @Override
  public ProviderName name() {
    return ProviderName.MONNIFY;
  }

  @Override
  public ProviderChargeResult initiateCharge(ChargeRequest request) {
    // paymentReference must be unique — Monnify's own idempotency mechanism, confirmed directly
    // from their docs — our idempotency key passes straight through.
    JsonNode response =
        client.initTransaction(
            Map.of(
                "amount", request.getAmountKobo(),
                "customerName", request.getCustomerEmail(),
                "customerEmail", request.getCustomerEmail(),
                "paymentReference", request.getIdempotencyKey(),
                "paymentDescription", "LendBridge top-up",
                "currencyCode", request.getCurrency(),
                "contractCode", contractCode));
    JsonNode body = response.path("responseBody");
    return ProviderChargeResult.builder()
        .providerReference(body.path("transactionReference").asText(request.getIdempotencyKey()))
        .status(ProviderOperationStatus.PENDING)
        .checkoutUrl(body.path("checkoutUrl").asText(null))
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public ProviderChargeResult verifyCharge(String providerReference) {
    // Monnify's transaction status arrives primarily via webhook (eventData.paymentStatus); there
    // is no separate "verify by reference" GET in the collections API the way Paystack/Flutterwave
    // have one, so this reports PENDING until the webhook updates the stored PaymentIntent
    // directly — callers should treat this method as a fallback, not the primary status source,
    // for this provider specifically.
    return ProviderChargeResult.builder().providerReference(providerReference).status(ProviderOperationStatus.PENDING).build();
  }

  @Override
  public ProviderVirtualAccountResult provisionVirtualAccount(VirtualAccountRequest request) {
    JsonNode response =
        client.reserveAccount(
            Map.of(
                "accountReference", request.getWalletId(),
                "accountName", request.getCustomerName(),
                "currencyCode", "NGN",
                "contractCode", contractCode,
                "customerEmail", request.getCustomerEmail()));
    JsonNode body = response.path("responseBody");
    JsonNode accounts = body.path("accounts");
    JsonNode firstAccount = accounts.isArray() && !accounts.isEmpty() ? accounts.get(0) : accounts;
    return ProviderVirtualAccountResult.builder()
        .accountNumber(firstAccount.path("accountNumber").asText())
        .bankName(firstAccount.path("bankName").asText())
        .providerReference(body.path("accountReference").asText())
        .build();
  }

  @Override
  public ProviderPayoutResult initiatePayout(PayoutRequest request) {
    JsonNode response =
        client.initiateDisbursement(
            Map.of(
                "amount", request.getAmountKobo(),
                "reference", request.getIdempotencyKey(),
                "narration", "LendBridge withdrawal",
                "destinationBankCode", request.getBankCode(),
                "destinationAccountNumber", request.getAccountNumber(),
                "destinationAccountName", request.getAccountHolderName(),
                "currency", request.getCurrency()));
    JsonNode body = response.path("responseBody");
    // Always PENDING here — see class javadoc: completion needs a human to submit the
    // merchant-email OTP out of band, never something this call can resolve synchronously.
    return ProviderPayoutResult.builder()
        .providerReference(body.path("reference").asText(request.getIdempotencyKey()))
        .status(ProviderOperationStatus.PENDING)
        .rawResponseJson(response.toString())
        .build();
  }

  @Override
  public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
    String provided = headers.get(SIGNATURE_HEADER);
    if (provided == null) {
      // Confirmed possibly-absent-in-sandbox; tolerate only outside production.
      return !productionEnvironment;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] computed = mac.doFinal(rawBody);
      String computedHex = java.util.HexFormat.of().formatHex(computed);
      return MessageDigest.isEqual(
          computedHex.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      log.error("Failed to verify Monnify webhook signature", e);
      return false;
    }
  }

  @Override
  public ProviderWebhookEvent parseWebhookEvent(byte[] rawBody, Map<String, String> headers) {
    try {
      JsonNode node = objectMapper.readTree(rawBody);
      String eventTypeRaw = node.path("eventType").asText("");
      JsonNode data = node.path("eventData");
      ProviderWebhookEventType eventType =
          switch (eventTypeRaw) {
            case "SUCCESSFUL_TRANSACTION" ->
                "PAID".equals(data.path("paymentStatus").asText(""))
                    ? ProviderWebhookEventType.CHARGE_SUCCEEDED
                    : ProviderWebhookEventType.CHARGE_FAILED;
            case "SUCCESSFUL_DISBURSEMENT" -> ProviderWebhookEventType.TRANSFER_SUCCESSFUL;
            case "FAILED_DISBURSEMENT", "REVERSED_DISBURSEMENT" -> ProviderWebhookEventType.TRANSFER_FAILED;
            default -> ProviderWebhookEventType.UNKNOWN;
          };
      return ProviderWebhookEvent.builder()
          .provider(ProviderName.MONNIFY)
          .eventType(eventType)
          .providerReference(data.path("transactionReference").asText(data.path("reference").asText(null)))
          .amountKobo(data.path("amountPaid").asLong(data.path("amount").asLong(0)))
          .rawPayloadJson(node.toString())
          .build();
    } catch (Exception e) {
      log.warn("Failed to parse Monnify webhook payload", e);
      return ProviderWebhookEvent.builder().provider(ProviderName.MONNIFY).eventType(ProviderWebhookEventType.UNKNOWN).build();
    }
  }
}

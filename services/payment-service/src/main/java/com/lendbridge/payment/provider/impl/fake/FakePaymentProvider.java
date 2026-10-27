package com.lendbridge.payment.provider.impl.fake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.lendbridge.payment.provider.SettlementRecord;
import com.lendbridge.payment.provider.SettlementReportSource;
import com.lendbridge.payment.provider.VirtualAccountRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fake PSP for local development and tests; see {@code FakeVirusScanner} in document-service for
 * the same pattern (CLAUDE.md's external-provider rule). Deterministic, EICAR-style: a reference
 * containing {@link #FAIL_SENTINEL} always fails — exactly as a real test-mode PSP would reject a
 * known-bad test reference — everything else succeeds synchronously. This is what every test and
 * CI run exercises; the three real adapters are never invoked by anything automated.
 */
@Component
public class FakePaymentProvider implements PaymentProvider, SettlementReportSource {

  private static final Logger log = LoggerFactory.getLogger(FakePaymentProvider.class);
  private static final String FAIL_SENTINEL = "SENTINEL_FAIL";
  private static final String SIGNATURE_HEADER = "x-fake-signature";
  private static final String VALID_SIGNATURE = "test-signature";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ProviderName name() {
    return ProviderName.FAKE;
  }

  @Override
  public ProviderChargeResult initiateCharge(ChargeRequest request) {
    boolean fail = request.getReference() != null && request.getReference().contains(FAIL_SENTINEL);
    ProviderOperationStatus status = fail ? ProviderOperationStatus.FAILED : ProviderOperationStatus.SUCCESSFUL;
    log.info("Fake charge {} for {} kobo: {}", request.getReference(), request.getAmountKobo(), status);
    return ProviderChargeResult.builder()
        .providerReference("fake-charge-" + UUID.randomUUID())
        .status(status)
        .checkoutUrl(null)
        .rawResponseJson("{\"fake\":true}")
        .build();
  }

  @Override
  public ProviderChargeResult verifyCharge(String providerReference) {
    return ProviderChargeResult.builder()
        .providerReference(providerReference)
        .status(ProviderOperationStatus.SUCCESSFUL)
        .rawResponseJson("{\"fake\":true}")
        .build();
  }

  @Override
  public ProviderVirtualAccountResult provisionVirtualAccount(VirtualAccountRequest request) {
    return ProviderVirtualAccountResult.builder()
        .accountNumber("00" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L))
        .bankName("Fake Bank")
        .providerReference("fake-va-" + UUID.randomUUID())
        .build();
  }

  @Override
  public ProviderPayoutResult initiatePayout(PayoutRequest request) {
    boolean fail = request.getReference() != null && request.getReference().contains(FAIL_SENTINEL);
    ProviderOperationStatus status = fail ? ProviderOperationStatus.FAILED : ProviderOperationStatus.SUCCESSFUL;
    log.info("Fake payout {} for {} kobo: {}", request.getReference(), request.getAmountKobo(), status);
    return ProviderPayoutResult.builder()
        .providerReference("fake-payout-" + UUID.randomUUID())
        .status(status)
        .rawResponseJson("{\"fake\":true}")
        .build();
  }

  @Override
  public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
    return VALID_SIGNATURE.equals(headers.get(SIGNATURE_HEADER));
  }

  @Override
  public ProviderWebhookEvent parseWebhookEvent(byte[] rawBody, Map<String, String> headers) {
    try {
      JsonNode node = objectMapper.readTree(rawBody);
      ProviderWebhookEventType eventType =
          ProviderWebhookEventType.valueOf(node.path("eventType").asText(ProviderWebhookEventType.UNKNOWN.name()));
      return ProviderWebhookEvent.builder()
          .provider(ProviderName.FAKE)
          .eventType(eventType)
          .providerReference(node.path("providerReference").asText(null))
          .amountKobo(node.path("amountKobo").asLong(0))
          .rawPayloadJson(new String(rawBody))
          .build();
    } catch (Exception e) {
      log.warn("Failed to parse fake webhook payload", e);
      return ProviderWebhookEvent.builder().provider(ProviderName.FAKE).eventType(ProviderWebhookEventType.UNKNOWN).build();
    }
  }

  @Override
  public List<SettlementRecord> generateSettlementReport(ProviderName provider, Instant from, Instant to) {
    // No real settlement file exists to test against; the fake has nothing recorded to report
    // beyond what the caller already knows from its own provider_transaction rows, so this
    // returns an empty report — reconciliation against the fake is exercised by constructing
    // SettlementRecord fixtures directly in tests, not by this method producing them.
    return List.of();
  }

  /** Builds a canned webhook JSON body + the matching signature header, for tests constructing
   * an inbound webhook request without depending on this class's internal JSON shape. */
  public static String buildWebhookBody(ProviderWebhookEventType eventType, String providerReference, long amountKobo) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("eventType", eventType.name());
    node.put("providerReference", providerReference);
    node.put("amountKobo", amountKobo);
    return node.toString();
  }

  public static Map<String, String> validSignatureHeader() {
    return Map.of(SIGNATURE_HEADER, VALID_SIGNATURE);
  }

  public static String failSentinelReference(String base) {
    return base + "-" + FAIL_SENTINEL;
  }
}

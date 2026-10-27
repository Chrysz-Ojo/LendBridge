package com.lendbridge.payment.service.impl;

import com.lendbridge.payment.model.PaymentIntent;
import com.lendbridge.payment.model.PaymentIntentStatus;
import com.lendbridge.payment.model.Payout;
import com.lendbridge.payment.model.PayoutStatus;
import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.model.WebhookLog;
import com.lendbridge.payment.outbox.KafkaTopics;
import com.lendbridge.payment.outbox.PaymentFailedPayload;
import com.lendbridge.payment.outbox.PaymentSucceededPayload;
import com.lendbridge.payment.provider.PaymentProvider;
import com.lendbridge.payment.provider.ProviderWebhookEvent;
import com.lendbridge.payment.repository.PaymentIntentRepository;
import com.lendbridge.payment.repository.PayoutRepository;
import com.lendbridge.payment.repository.WebhookLogRepository;
import com.lendbridge.payment.routing.PaymentProviderRouter;
import com.lendbridge.payment.service.WebhookService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place a webhook's raw body/headers are ever touched — signature verification is
 * per-adapter ({@code provider.verifySignature}), the dispatch logic here is provider-agnostic
 * once normalized into a {@link ProviderWebhookEvent}. Never calls ledger-service: success/failure
 * is only ever reported via {@code payment.succeeded}/{@code payment.failed}, which ledger-service
 * consumes itself (see ADR 0013) — a webhook request carries no end-user JWT to forward anyway.
 */
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

  private final WebhookLogRepository webhookLogRepository;
  private final PaymentIntentRepository paymentIntentRepository;
  private final PayoutRepository payoutRepository;
  private final PaymentProviderRouter paymentProviderRouter;
  private final OutboxEventWriter outboxEventWriter;
  private final IdempotencyOperations idempotencyOperations;

  @Override
  @Transactional
  public void processWebhook(String providerPathSegment, byte[] rawBody, Map<String, String> headers) {
    ProviderName providerName = resolveProvider(providerPathSegment);
    PaymentProvider provider = paymentProviderRouter.provider(providerName);

    boolean signatureValid = provider.verifySignature(rawBody, headers);
    if (!signatureValid) {
      webhookLogRepository.save(
          WebhookLog.builder()
              .id(UuidV7.generate())
              .provider(providerName)
              .providerEventId(providerName + ":invalid-signature:" + UuidV7.generate())
              .rawBody(new String(rawBody, StandardCharsets.UTF_8))
              .headersJson(headers.toString())
              .signatureValid(false)
              .processed(false)
              .receivedAt(Instant.now())
              .build());
      throw new BadRequestException("WEBHOOK_SIGNATURE_INVALID", "Webhook signature verification failed");
    }

    ProviderWebhookEvent event = provider.parseWebhookEvent(rawBody, headers);
    String providerEventId = providerName + ":" + event.getEventType() + ":" + event.getProviderReference();

    idempotencyOperations.runIdempotently(
        () -> webhookLogRepository.findByProviderEventId(providerEventId).map(existing -> true),
        () -> {
          handleEvent(providerName, event, rawBody, headers, providerEventId);
          return true;
        });
  }

  private void handleEvent(
      ProviderName providerName,
      ProviderWebhookEvent event,
      byte[] rawBody,
      Map<String, String> headers,
      String providerEventId) {
    UUID paymentIntentId = null;
    UUID payoutId = null;

    switch (event.getEventType()) {
      case CHARGE_SUCCEEDED -> paymentIntentId = handleChargeSucceeded(providerName, event);
      case CHARGE_FAILED -> paymentIntentId = handleChargeFailed(providerName, event);
      case TRANSFER_SUCCESSFUL -> payoutId = handleTransferSuccessful(providerName, event);
      case TRANSFER_FAILED -> payoutId = handleTransferFailed(providerName, event);
      case UNKNOWN -> log.info("Ignoring unrecognized webhook event from {}: {}", providerName, event.getRawPayloadJson());
    }

    webhookLogRepository.save(
        WebhookLog.builder()
            .id(UuidV7.generate())
            .provider(providerName)
            .providerEventId(providerEventId)
            .rawBody(new String(rawBody, StandardCharsets.UTF_8))
            .headersJson(headers.toString())
            .signatureValid(true)
            .processed(true)
            .paymentIntentId(paymentIntentId)
            .payoutId(payoutId)
            .receivedAt(Instant.now())
            .build());
  }

  private UUID handleChargeSucceeded(ProviderName providerName, ProviderWebhookEvent event) {
    return findPaymentIntent(providerName, event)
        .map(
            paymentIntent -> {
              if (paymentIntent.getStatus() == PaymentIntentStatus.SUCCEEDED) {
                return paymentIntent.getId();
              }
              Instant now = Instant.now();
              paymentIntent.setStatus(PaymentIntentStatus.SUCCEEDED);
              paymentIntent.setUpdatedAt(now);
              paymentIntentRepository.save(paymentIntent);

              outboxEventWriter.write(
                  "PaymentIntent",
                  paymentIntent.getId().toString(),
                  KafkaTopics.PAYMENT_SUCCEEDED,
                  PaymentSucceededPayload.builder()
                      .paymentReferenceId(paymentIntent.getId())
                      .purpose(paymentIntent.getPurpose().name())
                      .walletLedgerAccountId(paymentIntent.getWalletLedgerAccountId())
                      .amountKobo(paymentIntent.getAmountKobo())
                      .currency(paymentIntent.getCurrency())
                      .idempotencyKey(paymentIntent.getIdempotencyKey())
                      .occurredAtEpochMillis(now.toEpochMilli())
                      .build());
              return paymentIntent.getId();
            })
        .orElse(null);
  }

  private UUID handleChargeFailed(ProviderName providerName, ProviderWebhookEvent event) {
    return findPaymentIntent(providerName, event)
        .map(
            paymentIntent -> {
              if (paymentIntent.getStatus() == PaymentIntentStatus.FAILED
                  || paymentIntent.getStatus() == PaymentIntentStatus.SUCCEEDED) {
                return paymentIntent.getId();
              }
              Instant now = Instant.now();
              paymentIntent.setStatus(PaymentIntentStatus.FAILED);
              paymentIntent.setUpdatedAt(now);
              paymentIntentRepository.save(paymentIntent);

              outboxEventWriter.write(
                  "PaymentIntent",
                  paymentIntent.getId().toString(),
                  KafkaTopics.PAYMENT_FAILED,
                  PaymentFailedPayload.builder()
                      .paymentReferenceId(paymentIntent.getId())
                      .purpose(paymentIntent.getPurpose().name())
                      .reason("Charge failed at provider")
                      .occurredAtEpochMillis(now.toEpochMilli())
                      .build());
              return paymentIntent.getId();
            })
        .orElse(null);
  }

  private UUID handleTransferSuccessful(ProviderName providerName, ProviderWebhookEvent event) {
    return findPayout(providerName, event)
        .map(
            payout -> {
              if (payout.getStatus() == PayoutStatus.SUCCESSFUL) {
                return payout.getId();
              }
              Instant now = Instant.now();
              payout.setStatus(PayoutStatus.SUCCESSFUL);
              payout.setUpdatedAt(now);
              payoutRepository.save(payout);

              outboxEventWriter.write(
                  "Payout",
                  payout.getId().toString(),
                  KafkaTopics.PAYMENT_SUCCEEDED,
                  PaymentSucceededPayload.builder()
                      .paymentReferenceId(payout.getId())
                      .purpose("PAYOUT")
                      .walletLedgerAccountId(payout.getWalletLedgerAccountId())
                      .amountKobo(payout.getAmountKobo())
                      .currency(payout.getCurrency())
                      .idempotencyKey(payout.getIdempotencyKey())
                      .occurredAtEpochMillis(now.toEpochMilli())
                      .build());
              return payout.getId();
            })
        .orElse(null);
  }

  private UUID handleTransferFailed(ProviderName providerName, ProviderWebhookEvent event) {
    return findPayout(providerName, event)
        .map(
            payout -> {
              if (payout.getStatus() == PayoutStatus.FAILED || payout.getStatus() == PayoutStatus.SUCCESSFUL) {
                return payout.getId();
              }
              Instant now = Instant.now();
              payout.setStatus(PayoutStatus.FAILED);
              payout.setUpdatedAt(now);
              payoutRepository.save(payout);

              outboxEventWriter.write(
                  "Payout",
                  payout.getId().toString(),
                  KafkaTopics.PAYMENT_FAILED,
                  PaymentFailedPayload.builder()
                      .paymentReferenceId(payout.getId())
                      .purpose("PAYOUT")
                      .reason("Transfer failed at provider")
                      .occurredAtEpochMillis(now.toEpochMilli())
                      .build());
              return payout.getId();
            })
        .orElse(null);
  }

  private Optional<PaymentIntent> findPaymentIntent(ProviderName providerName, ProviderWebhookEvent event) {
    Optional<PaymentIntent> found =
        paymentIntentRepository.findByProviderAndProviderReference(providerName, event.getProviderReference());
    if (found.isEmpty()) {
      log.warn(
          "No PaymentIntent found for {} reference {}", providerName, event.getProviderReference());
    }
    return found;
  }

  private Optional<Payout> findPayout(ProviderName providerName, ProviderWebhookEvent event) {
    Optional<Payout> found =
        payoutRepository.findByProviderAndProviderReference(providerName, event.getProviderReference());
    if (found.isEmpty()) {
      log.warn("No Payout found for {} reference {}", providerName, event.getProviderReference());
    }
    return found;
  }

  private ProviderName resolveProvider(String providerPathSegment) {
    try {
      return ProviderName.valueOf(providerPathSegment.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new NotFoundException("UNKNOWN_PAYMENT_PROVIDER", "Unknown payment provider: " + providerPathSegment);
    }
  }
}

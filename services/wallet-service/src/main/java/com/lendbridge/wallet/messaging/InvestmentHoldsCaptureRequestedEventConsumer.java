package com.lendbridge.wallet.messaging;

import com.lendbridge.investment.avro.InvestmentHoldsCaptureRequested;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.model.ProcessedEvent;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletHoldsCaptureAssembledPayload;
import com.lendbridge.wallet.repository.ProcessedEventRepository;
import com.lendbridge.wallet.service.HoldService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Funding saga Stage 4: flips every named lender hold {@code ACTIVE -> CONVERTED}. Not itself
 * {@code @Transactional} — {@code HoldService.captureHolds} has its own all-or-nothing transaction
 * boundary that must roll back independently of the outbox write that reports the outcome, same
 * reasoning as {@code InvestmentDisbursementReadyEventConsumer} in ledger-service.
 */
@Component
@RequiredArgsConstructor
public class InvestmentHoldsCaptureRequestedEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(InvestmentHoldsCaptureRequestedEventConsumer.class);

  private final HoldService holdService;
  private final ProcessedEventRepository processedEventRepository;
  private final OutboxEventWriter outboxEventWriter;

  @KafkaListener(topics = "investment.holds.capture-requested", groupId = "wallet-service")
  public void onCaptureRequested(
      ConsumerRecord<String, InvestmentHoldsCaptureRequested> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.holds.capture-requested event {}", eventId);
      return;
    }

    InvestmentHoldsCaptureRequested payload = record.value();
    String loanId = payload.getLoanId().toString();
    List<String> holdIds = payload.getHoldIds().stream().map(CharSequence::toString).toList();
    Instant now = Instant.now();

    boolean success;
    String failureReason = null;
    try {
      holdService.captureHolds(holdIds.stream().map(UUID::fromString).toList());
      success = true;
    } catch (Exception e) {
      log.error("Failed to capture holds {} for loan {}", holdIds, loanId, e);
      success = false;
      failureReason = e.getMessage();
    }

    outboxEventWriter.write(
        "Loan",
        loanId,
        KafkaTopics.WALLET_HOLDS_CAPTURE_ASSEMBLED,
        new WalletHoldsCaptureAssembledPayload(
            loanId, success, holdIds, failureReason, now.toEpochMilli()));

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}

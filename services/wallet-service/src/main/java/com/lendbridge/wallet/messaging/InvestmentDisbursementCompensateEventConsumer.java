package com.lendbridge.wallet.messaging;

import com.lendbridge.investment.avro.InvestmentDisbursementCompensate;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.model.ProcessedEvent;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletHoldsRestoredPayload;
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
 * The funding saga's one compensating action: if disbursement fails after holds were already
 * captured, flip them back {@code CONVERTED -> ACTIVE}. {@code HoldService.restoreHolds} is itself
 * idempotent against redelivery (already-{@code ACTIVE} holds are left alone), so this consumer
 * doesn't need the capture consumer's success/failure branching — it always reports success once
 * the restore call returns.
 */
@Component
@RequiredArgsConstructor
public class InvestmentDisbursementCompensateEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(InvestmentDisbursementCompensateEventConsumer.class);

  private final HoldService holdService;
  private final ProcessedEventRepository processedEventRepository;
  private final OutboxEventWriter outboxEventWriter;

  @KafkaListener(topics = "investment.disbursement.compensate", groupId = "wallet-service")
  public void onCompensate(
      ConsumerRecord<String, InvestmentDisbursementCompensate> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.disbursement.compensate event {}", eventId);
      return;
    }

    InvestmentDisbursementCompensate payload = record.value();
    String loanId = payload.getLoanId().toString();
    List<String> holdIds = payload.getHoldIds().stream().map(CharSequence::toString).toList();
    Instant now = Instant.now();

    holdService.restoreHolds(holdIds.stream().map(UUID::fromString).toList());

    outboxEventWriter.write(
        "Loan",
        loanId,
        KafkaTopics.WALLET_HOLDS_RESTORED,
        new WalletHoldsRestoredPayload(loanId, holdIds, now.toEpochMilli()));

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}

package com.lendbridge.reporting.messaging;

import com.lendbridge.investment.avro.InvestmentCancelled;
import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.reporting.model.InvestmentActivityFact;
import com.lendbridge.reporting.model.InvestmentActivityType;
import com.lendbridge.reporting.model.ProcessedEvent;
import com.lendbridge.reporting.repository.InvestmentActivityFactRepository;
import com.lendbridge.reporting.repository.ProcessedEventRepository;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@code investment.cancelled} carries no {@code lenderUserId} (confirmed on the copied Avro
 * schema) — {@link InvestmentActivityFact#getLenderUserId()} is left null for those rows. */
@Component
@RequiredArgsConstructor
public class InvestmentActivityEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentActivityEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final InvestmentActivityFactRepository investmentActivityFactRepository;

  @KafkaListener(topics = "investment.committed", groupId = "reporting-service")
  @Transactional
  public void onInvestmentCommitted(
      ConsumerRecord<String, InvestmentCommitted> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.committed event {}", eventId);
      return;
    }
    InvestmentCommitted payload = record.value();
    investmentActivityFactRepository.save(
        InvestmentActivityFact.builder()
            .id(UuidV7.generate())
            .commitmentId(UUID.fromString(payload.getCommitmentId().toString()))
            .loanId(UUID.fromString(payload.getLoanId().toString()))
            .lenderUserId(UUID.fromString(payload.getLenderUserId().toString()))
            .amountKobo(payload.getAmountKobo())
            .activityType(InvestmentActivityType.COMMITTED)
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());
    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  @KafkaListener(topics = "investment.cancelled", groupId = "reporting-service")
  @Transactional
  public void onInvestmentCancelled(
      ConsumerRecord<String, InvestmentCancelled> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.cancelled event {}", eventId);
      return;
    }
    InvestmentCancelled payload = record.value();
    investmentActivityFactRepository.save(
        InvestmentActivityFact.builder()
            .id(UuidV7.generate())
            .commitmentId(UUID.fromString(payload.getCommitmentId().toString()))
            .loanId(UUID.fromString(payload.getLoanId().toString()))
            .lenderUserId(null)
            .amountKobo(payload.getAmountKobo())
            .activityType(InvestmentActivityType.CANCELLED)
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());
    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  private static String idFor(ConsumerRecord<String, ?> record, byte[] ceIdHeader) {
    String ceId = ceIdHeader != null ? new String(ceIdHeader) : null;
    return record.topic() + ":" + (ceId != null ? ceId : String.valueOf(record.offset()));
  }
}

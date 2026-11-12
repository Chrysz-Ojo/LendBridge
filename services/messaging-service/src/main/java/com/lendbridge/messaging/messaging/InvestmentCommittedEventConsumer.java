package com.lendbridge.messaging.messaging;

import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.messaging.model.ProcessedEvent;
import com.lendbridge.messaging.repository.ProcessedEventRepository;
import com.lendbridge.messaging.service.ConversationService;
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

/** Phase 1 chat-eligibility signal (ADR 0019: "committed" only, "viewed" deferred). Idempotent
 * against redelivery via {@code processed_event}, same pattern as every other service's
 * consumers. */
@Component
@RequiredArgsConstructor
public class InvestmentCommittedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentCommittedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final ConversationService conversationService;

  @KafkaListener(topics = "investment.committed", groupId = "messaging-service")
  @Transactional
  public void onInvestmentCommitted(
      ConsumerRecord<String, InvestmentCommitted> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.committed event {}", eventId);
      return;
    }

    InvestmentCommitted payload = record.value();
    conversationService.openOnCommitment(
        UUID.fromString(payload.getLoanId().toString()),
        UUID.fromString(payload.getLenderUserId().toString()),
        Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()));

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}

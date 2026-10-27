package com.lendbridge.ledger.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.ledger.avro.LedgerPosted;
import com.lendbridge.ledger.avro.LedgerPosting;
import com.lendbridge.starter.outbox.AbstractOutboxRelay;
import com.lendbridge.starter.outbox.CloudEventHeaders;
import com.lendbridge.starter.outbox.OutboxEvent;
import com.lendbridge.starter.outbox.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay extends AbstractOutboxRelay {

  private final ObjectMapper objectMapper;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final CloudEventHeaders cloudEventHeaders;

  public OutboxRelay(
      OutboxEventRepository repository,
      ObjectMapper objectMapper,
      KafkaTemplate<String, Object> kafkaTemplate,
      CloudEventHeaders cloudEventHeaders) {
    super(repository);
    this.objectMapper = objectMapper;
    this.kafkaTemplate = kafkaTemplate;
    this.cloudEventHeaders = cloudEventHeaders;
  }

  @Override
  protected void publish(OutboxEvent event) throws Exception {
    switch (event.getEventType()) {
      case KafkaTopics.LEDGER_POSTED -> publishLedgerPosted(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishLedgerPosted(OutboxEvent event) throws Exception {
    LedgerPostedPayload payload = objectMapper.readValue(event.getPayload(), LedgerPostedPayload.class);
    List<LedgerPosting> postings =
        payload.getPostings().stream()
            .map(
                p ->
                    LedgerPosting.newBuilder()
                        .setPostingId(p.getPostingId())
                        .setAccountId(p.getAccountId())
                        .setDirection(p.getDirection())
                        .setAmountKobo(p.getAmountKobo())
                        .build())
            .toList();
    LedgerPosted avroValue =
        LedgerPosted.newBuilder()
            .setJournalEntryId(payload.getJournalEntryId())
            .setIdempotencyKey(payload.getIdempotencyKey())
            .setCurrency(payload.getCurrency())
            .setReference(payload.getReference())
            .setCorrelationId(payload.getCorrelationId())
            .setPostedAtEpochMillis(payload.getPostedAtEpochMillis())
            .setPostings(postings)
            .build();
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(
            KafkaTopics.LEDGER_POSTED,
            null,
            event.getAggregateId(),
            avroValue,
            cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

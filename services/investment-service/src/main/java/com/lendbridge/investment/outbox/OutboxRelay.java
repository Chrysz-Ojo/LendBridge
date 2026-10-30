package com.lendbridge.investment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.investment.avro.InvestmentCancelled;
import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.starter.outbox.AbstractOutboxRelay;
import com.lendbridge.starter.outbox.CloudEventHeaders;
import com.lendbridge.starter.outbox.OutboxEvent;
import com.lendbridge.starter.outbox.OutboxEventRepository;
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
      case KafkaTopics.INVESTMENT_COMMITTED -> publishInvestmentCommitted(event);
      case KafkaTopics.INVESTMENT_CANCELLED -> publishInvestmentCancelled(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishInvestmentCommitted(OutboxEvent event) throws Exception {
    InvestmentCommittedPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentCommittedPayload.class);
    InvestmentCommitted avroValue =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(payload.getCommitmentId().toString())
            .setLoanId(payload.getLoanId().toString())
            .setLenderUserId(payload.getLenderUserId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_COMMITTED, event, avroValue);
  }

  private void publishInvestmentCancelled(OutboxEvent event) throws Exception {
    InvestmentCancelledPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentCancelledPayload.class);
    InvestmentCancelled avroValue =
        InvestmentCancelled.newBuilder()
            .setCommitmentId(payload.getCommitmentId().toString())
            .setLoanId(payload.getLoanId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_CANCELLED, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

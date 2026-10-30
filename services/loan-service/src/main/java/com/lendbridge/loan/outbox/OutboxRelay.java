package com.lendbridge.loan.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.loan.avro.LoanSubmitted;
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
      case KafkaTopics.LOAN_SUBMITTED -> publishLoanSubmitted(event);
      case KafkaTopics.LOAN_LISTED -> publishLoanListed(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishLoanSubmitted(OutboxEvent event) throws Exception {
    LoanSubmittedPayload payload = objectMapper.readValue(event.getPayload(), LoanSubmittedPayload.class);
    LoanSubmitted avroValue =
        LoanSubmitted.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_SUBMITTED, event, avroValue);
  }

  private void publishLoanListed(OutboxEvent event) throws Exception {
    LoanListedPayload payload = objectMapper.readValue(event.getPayload(), LoanListedPayload.class);
    LoanListed avroValue =
        LoanListed.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setInterestRateBps(payload.getInterestRateBps())
            .setTenorMonths(payload.getTenorMonths())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_LISTED, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

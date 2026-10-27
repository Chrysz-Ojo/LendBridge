package com.lendbridge.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.payment.avro.PaymentFailed;
import com.lendbridge.payment.avro.PaymentSucceeded;
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
      case KafkaTopics.PAYMENT_SUCCEEDED -> publishPaymentSucceeded(event);
      case KafkaTopics.PAYMENT_FAILED -> publishPaymentFailed(event);
      default ->
          throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishPaymentSucceeded(OutboxEvent event) throws Exception {
    PaymentSucceededPayload payload =
        objectMapper.readValue(event.getPayload(), PaymentSucceededPayload.class);
    PaymentSucceeded avroValue =
        PaymentSucceeded.newBuilder()
            .setPaymentReferenceId(payload.getPaymentReferenceId().toString())
            .setPurpose(payload.getPurpose())
            .setWalletLedgerAccountId(payload.getWalletLedgerAccountId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setCurrency(payload.getCurrency())
            .setIdempotencyKey(payload.getIdempotencyKey())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.PAYMENT_SUCCEEDED, event, avroValue);
  }

  private void publishPaymentFailed(OutboxEvent event) throws Exception {
    PaymentFailedPayload payload =
        objectMapper.readValue(event.getPayload(), PaymentFailedPayload.class);
    PaymentFailed avroValue =
        PaymentFailed.newBuilder()
            .setPaymentReferenceId(payload.getPaymentReferenceId().toString())
            .setPurpose(payload.getPurpose())
            .setReason(payload.getReason())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.PAYMENT_FAILED, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(
            topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

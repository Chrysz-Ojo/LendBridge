package com.lendbridge.kyc.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.kyc.avro.KycRejected;
import com.lendbridge.kyc.avro.KycVerified;
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
      case KafkaTopics.KYC_VERIFIED -> publishKycVerified(event);
      case KafkaTopics.KYC_REJECTED -> publishKycRejected(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishKycVerified(OutboxEvent event) throws Exception {
    KycVerifiedPayload payload = objectMapper.readValue(event.getPayload(), KycVerifiedPayload.class);
    KycVerified avroValue =
        KycVerified.newBuilder()
            .setVerificationCaseId(payload.getVerificationCaseId().toString())
            .setUserId(payload.getUserId().toString())
            .setTrustTier(payload.getTrustTier())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.KYC_VERIFIED, event, avroValue);
  }

  private void publishKycRejected(OutboxEvent event) throws Exception {
    KycRejectedPayload payload = objectMapper.readValue(event.getPayload(), KycRejectedPayload.class);
    KycRejected avroValue =
        KycRejected.newBuilder()
            .setVerificationCaseId(payload.getVerificationCaseId().toString())
            .setUserId(payload.getUserId().toString())
            .setReason(payload.getReason())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.KYC_REJECTED, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

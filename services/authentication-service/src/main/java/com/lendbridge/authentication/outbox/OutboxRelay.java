package com.lendbridge.authentication.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.authentication.avro.UserRegistered;
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
      case KafkaTopics.USER_REGISTERED -> publishUserRegistered(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishUserRegistered(OutboxEvent event) throws Exception {
    UserRegisteredPayload payload =
        objectMapper.readValue(event.getPayload(), UserRegisteredPayload.class);
    UserRegistered avroValue =
        UserRegistered.newBuilder()
            .setUserId(payload.getUserId())
            .setEmail(payload.getEmail())
            .setEmailVerified(payload.isEmailVerified())
            .setPhoneVerified(payload.isPhoneVerified())
            .setRegisteredAtEpochMillis(payload.getRegisteredAtEpochMillis())
            .build();
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(
            KafkaTopics.USER_REGISTERED,
            null,
            event.getAggregateId(),
            avroValue,
            cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

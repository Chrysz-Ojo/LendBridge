package com.lendbridge.adminbff.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.adminbff.avro.AdminAction;
import com.lendbridge.starter.outbox.AbstractOutboxRelay;
import com.lendbridge.starter.outbox.CloudEventHeaders;
import com.lendbridge.starter.outbox.OutboxEvent;
import com.lendbridge.starter.outbox.OutboxEventRepository;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Both {@code admin.action.approved} and {@code admin.action.rejected} share the same
 * {@code AdminAction} Avro shape — only the topic and the payload's own {@code status} field
 * differ, so one publish method handles both. */
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
      case KafkaTopics.ADMIN_ACTION_APPROVED, KafkaTopics.ADMIN_ACTION_REJECTED -> publishAdminAction(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishAdminAction(OutboxEvent event) throws Exception {
    AdminActionPayload payload = objectMapper.readValue(event.getPayload(), AdminActionPayload.class);
    AdminAction avroValue =
        AdminAction.newBuilder()
            .setApprovalId(payload.getApprovalId().toString())
            .setActionType(payload.getActionType())
            .setRaisedByUserId(payload.getRaisedByUserId().toString())
            .setDecidedByUserId(payload.getDecidedByUserId().toString())
            .setStatus(payload.getStatus())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(event.getEventType(), event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

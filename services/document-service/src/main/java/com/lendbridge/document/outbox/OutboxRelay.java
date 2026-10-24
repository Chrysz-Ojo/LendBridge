package com.lendbridge.document.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.document.avro.DocumentUploaded;
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
      case KafkaTopics.DOCUMENT_UPLOADED -> publishDocumentUploaded(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishDocumentUploaded(OutboxEvent event) throws Exception {
    DocumentUploadedPayload payload =
        objectMapper.readValue(event.getPayload(), DocumentUploadedPayload.class);
    DocumentUploaded avroValue =
        DocumentUploaded.newBuilder()
            .setDocumentId(payload.getDocumentId())
            .setOwnerId(payload.getOwnerId())
            .setDocumentType(payload.getDocumentType())
            .setUploadedAtEpochMillis(payload.getUploadedAtEpochMillis())
            .build();
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(
            KafkaTopics.DOCUMENT_UPLOADED,
            null,
            event.getAggregateId(),
            avroValue,
            cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

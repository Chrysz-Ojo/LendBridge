package com.lendbridge.starter.outbox;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hand-rolled CloudEvents attributes carried as Kafka record headers, per the PRD's event
 * envelope convention. No CloudEvents SDK dependency is added. Every service supplies its own
 * {@code ce_source} value under the same property name, the same way each supplies its own
 * {@code platform.field-encryption.key}.
 */
@Component
public class CloudEventHeaders {

  private final String source;

  public CloudEventHeaders(@Value("${platform.outbox.ce-source}") String source) {
    this.source = source;
  }

  public RecordHeaders forEvent(OutboxEvent event) {
    RecordHeaders headers = new RecordHeaders();
    headers.add(new RecordHeader("ce_id", event.getId().toString().getBytes(StandardCharsets.UTF_8)));
    headers.add(new RecordHeader("ce_source", source.getBytes(StandardCharsets.UTF_8)));
    headers.add(new RecordHeader("ce_type", event.getEventType().getBytes(StandardCharsets.UTF_8)));
    headers.add(
        new RecordHeader(
            "ce_time",
            DateTimeFormatter.ISO_INSTANT
                .format(event.getCreatedAt())
                .getBytes(StandardCharsets.UTF_8)));
    headers.add(
        new RecordHeader("ce_subject", event.getAggregateId().getBytes(StandardCharsets.UTF_8)));
    return headers;
  }
}

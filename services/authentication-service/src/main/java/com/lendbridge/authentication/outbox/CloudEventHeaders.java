package com.lendbridge.authentication.outbox;

import com.lendbridge.authentication.model.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;

/**
 * Hand-rolled CloudEvents attributes carried as Kafka record headers, per the PRD's event
 * envelope convention. No CloudEvents SDK dependency is added.
 */
public final class CloudEventHeaders {

  private static final String SOURCE = "lendbridge://authentication-service";

  private CloudEventHeaders() {}

  public static RecordHeaders forEvent(OutboxEvent event) {
    RecordHeaders headers = new RecordHeaders();
    headers.add(new RecordHeader("ce_id", event.getId().toString().getBytes(StandardCharsets.UTF_8)));
    headers.add(new RecordHeader("ce_source", SOURCE.getBytes(StandardCharsets.UTF_8)));
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

package com.lendbridge.support.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lendbridge.support.avro.TicketUpdated;
import com.lendbridge.support.outbox.KafkaTopics;
import com.lendbridge.support.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Regression guard mirroring loan-service's/risk-service's own Kafka-consuming IT style: assert
 * a real Avro record lands on the topic, not just that the outbox row was written. */
class TicketUpdatedOutboxIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPPORT_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPPORT");

  @Test
  void staffReply_publishesRealTicketUpdatedKafkaRecord() throws Exception {
    UUID userId = UUID.randomUUID();
    String created =
        mockMvc
            .perform(
                post("/api/v1/support/tickets")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"subject":"Help","category":"TECHNICAL","body":"App won't load."}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ticketId = JsonPath.read(created, "$.data.id");

    mockMvc
        .perform(
            post("/api/v1/support/tickets/" + ticketId + "/messages")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"body":"We're on it."}
                    """))
        .andExpect(status().isOk());

    assertTicketUpdatedPublished(ticketId, "IN_PROGRESS", "STAFF");
  }

  private void assertTicketUpdatedPublished(String ticketId, String expectedStatus, String expectedAuthorRole) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroDeserializer");
    props.put("schema.registry.url", schemaRegistryUrl());
    props.put("specific.avro.reader", "true");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (KafkaConsumer<String, TicketUpdated> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of(KafkaTopics.TICKET_UPDATED));
      long deadline = System.currentTimeMillis() + 15_000;
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, TicketUpdated> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, TicketUpdated> record : records) {
          if (record.key().equals(ticketId)) {
            assertThat(record.value().getStatus().toString()).isEqualTo(expectedStatus);
            assertThat(record.value().getLastMessageAuthorRole().toString()).isEqualTo(expectedAuthorRole);
            return;
          }
        }
      }
    }
    throw new AssertionError("Did not receive ticket.updated Kafka message for ticket " + ticketId);
  }
}

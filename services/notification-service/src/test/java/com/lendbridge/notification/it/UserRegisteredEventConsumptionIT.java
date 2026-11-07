package com.lendbridge.notification.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.notification.support.AbstractIntegrationTest;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class UserRegisteredEventConsumptionIT extends AbstractIntegrationTest {

  @Test
  void consumingUserRegistered_createsContactAndSendsWelcomeEmail_andIsIdempotentOnRedelivery() {
    UUID userId = UUID.randomUUID();
    String email = "redelivered-" + userId + "@example.com";
    String eventId = UUID.randomUUID().toString();

    UserRegistered payload =
        UserRegistered.newBuilder()
            .setUserId(userId.toString())
            .setEmail(email)
            .setEmailVerified(true)
            .setPhoneVerified(true)
            .setRegisteredAtEpochMillis(System.currentTimeMillis())
            .build();

    publish(eventId, userId.toString(), payload);
    publish(eventId, userId.toString(), payload);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(contactRepository.findById(userId)).isPresent());

    assertThat(contactRepository.findById(userId).orElseThrow().getEmail()).isEqualTo(email);
    assertThat(notificationRepository.findAll().stream().filter(n -> n.getUserId().equals(userId))).hasSize(1);
    assertThat(processedEventRepository.existsById(eventId)).isTrue();
  }

  private void publish(String eventId, String key, UserRegistered payload) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    props.put("schema.registry.url", schemaRegistryUrl());

    try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props)) {
      ProducerRecord<String, Object> record = new ProducerRecord<>("user.registered", null, key, payload);
      record.headers().add(new RecordHeader("ce_id", eventId.getBytes()));
      producer.send(record).get();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish test user.registered event", e);
    }
  }
}

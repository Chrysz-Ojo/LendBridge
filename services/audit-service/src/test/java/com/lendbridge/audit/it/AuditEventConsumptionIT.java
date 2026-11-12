package com.lendbridge.audit.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.audit.model.AuditRecord;
import com.lendbridge.audit.support.AbstractIntegrationTest;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

/**
 * Proves the topic-pattern/{@code GenericRecord} design actually works end-to-end: two different,
 * unrelated topics (shaped like {@code loan.submitted} and {@code kyc.verified}, defined inline
 * below rather than depending on loan-service's/kyc-service's generated Avro classes) both land as
 * {@code AuditRecord} rows through the single listener, with no per-topic wiring in this service.
 */
class AuditEventConsumptionIT extends AbstractIntegrationTest {

  private static final Schema LOAN_SUBMITTED_SCHEMA =
      new Schema.Parser()
          .parse(
              """
              {
                "type": "record",
                "name": "LoanSubmitted",
                "namespace": "com.lendbridge.loan.avro",
                "fields": [
                  {"name": "loanId", "type": "string"},
                  {"name": "borrowerUserId", "type": "string"},
                  {"name": "occurredAtEpochMillis", "type": "long"}
                ]
              }
              """);

  private static final Schema KYC_VERIFIED_SCHEMA =
      new Schema.Parser()
          .parse(
              """
              {
                "type": "record",
                "name": "KycVerified",
                "namespace": "com.lendbridge.kyc.avro",
                "fields": [
                  {"name": "verificationCaseId", "type": "string"},
                  {"name": "userId", "type": "string"},
                  {"name": "trustTier", "type": "string"},
                  {"name": "occurredAtEpochMillis", "type": "long"}
                ]
              }
              """);

  @Test
  void differentTopics_bothRecordedByTheSameGenericListener() {
    String loanId = UUID.randomUUID().toString();
    String userId = UUID.randomUUID().toString();
    long occurredAt = Instant.now().toEpochMilli();

    GenericRecord loanSubmitted = new GenericData.Record(LOAN_SUBMITTED_SCHEMA);
    loanSubmitted.put("loanId", loanId);
    loanSubmitted.put("borrowerUserId", userId);
    loanSubmitted.put("occurredAtEpochMillis", occurredAt);

    GenericRecord kycVerified = new GenericData.Record(KYC_VERIFIED_SCHEMA);
    kycVerified.put("verificationCaseId", UUID.randomUUID().toString());
    kycVerified.put("userId", userId);
    kycVerified.put("trustTier", "TIER_2");
    kycVerified.put("occurredAtEpochMillis", occurredAt);

    publish("loan.submitted", loanId, loanSubmitted);
    publish("kyc.verified", userId, kycVerified);

    AuditRecord loanRecord = awaitRecord("loan.submitted", loanId);
    assertThat(loanRecord.getPayload()).contains(loanId);
    assertThat(loanRecord.getOccurredAt()).isEqualTo(Instant.ofEpochMilli(occurredAt));

    AuditRecord kycRecord = awaitRecord("kyc.verified", userId);
    assertThat(kycRecord.getPayload()).contains("TIER_2");
  }

  private void publish(String topic, String key, GenericRecord value) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl());

    try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(props)) {
      producer.send(new ProducerRecord<>(topic, key, value)).get();
    } catch (Exception e) {
      throw new RuntimeException("Failed to publish test record to " + topic, e);
    }
  }

  private AuditRecord awaitRecord(String topic, String aggregateKey) {
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      Optional<AuditRecord> found =
          auditRecordRepository.findAll().stream()
              .filter(r -> topic.equals(r.getTopic()) && aggregateKey.equals(r.getAggregateKey()))
              .findFirst();
      if (found.isPresent()) {
        return found.get();
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    throw new AssertionError("No AuditRecord recorded for topic=" + topic + " aggregateKey=" + aggregateKey);
  }
}

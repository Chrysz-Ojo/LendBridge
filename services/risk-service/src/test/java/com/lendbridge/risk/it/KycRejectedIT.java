package com.lendbridge.risk.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.lendbridge.kyc.avro.KycRejected;
import com.lendbridge.loan.avro.LoanSubmitted;
import com.lendbridge.risk.model.RiskAssessmentStatus;
import com.lendbridge.risk.support.AbstractIntegrationTest;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

/**
 * There is no "ungraded" event in the catalogue — a rejected borrower's pending assessments flip
 * to {@code KYC_REJECTED} locally, but {@code risk.graded} must never be published for them (see
 * ADR 0018). This is a negative/timeout assertion: the row reaching {@code KYC_REJECTED} is proof
 * enough that the consumer ran, so a short poll of the real topic afterwards is a reliable check
 * that nothing landed.
 */
class KycRejectedIT extends AbstractIntegrationTest {

  @Test
  void kycRejected_flipsPendingAssessmentToKycRejected_andNeverPublishesRiskGraded() {
    UUID loanId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();

    publishLoanSubmitted(loanId, borrowerUserId, 1_000_000L, 12, 1500);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(riskAssessmentRepository.findById(loanId))
                    .get()
                    .extracting(a -> a.getStatus())
                    .isEqualTo(RiskAssessmentStatus.PENDING_KYC));

    publishKycRejected(borrowerUserId, "selfie did not match ID photo");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(riskAssessmentRepository.findById(loanId))
                    .get()
                    .extracting(a -> a.getStatus())
                    .isEqualTo(RiskAssessmentStatus.KYC_REJECTED));

    assertThat(riskAssessmentRepository.findById(loanId).orElseThrow().getKycRejectionReason())
        .isEqualTo("selfie did not match ID photo");

    assertThat(noRiskGradedRecordArrivesWithin(Duration.ofSeconds(5))).isTrue();
  }

  private boolean noRiskGradedRecordArrivesWithin(Duration timeout) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroDeserializer");
    props.put("schema.registry.url", schemaRegistryUrl());
    props.put("specific.avro.reader", "true");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(java.util.List.of("risk.graded"));
      long deadline = System.currentTimeMillis() + timeout.toMillis();
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
        if (!records.isEmpty()) {
          return false;
        }
      }
    }
    return true;
  }

  private void publishLoanSubmitted(
      UUID loanId, UUID borrowerUserId, long amountKobo, int tenorMonths, int interestRateBps) {
    LoanSubmitted payload =
        LoanSubmitted.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(borrowerUserId.toString())
            .setAmountKobo(amountKobo)
            .setTenorMonths(tenorMonths)
            .setInterestRateBps(interestRateBps)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    publish("loan.submitted", loanId.toString(), payload);
  }

  private void publishKycRejected(UUID userId, String reason) {
    KycRejected payload =
        KycRejected.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(userId.toString())
            .setReason(reason)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    publish("kyc.rejected", userId.toString(), payload);
  }

  private void publish(String topic, String key, Object payload) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    props.put("schema.registry.url", schemaRegistryUrl());

    try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props)) {
      ProducerRecord<String, Object> record = new ProducerRecord<>(topic, null, key, payload);
      record.headers().add(new RecordHeader("ce_id", UUID.randomUUID().toString().getBytes()));
      producer.send(record).get();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish test " + topic + " event", e);
    }
  }
}

package com.lendbridge.risk.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.lendbridge.kyc.avro.KycVerified;
import com.lendbridge.loan.avro.LoanSubmitted;
import com.lendbridge.risk.model.RiskAssessmentStatus;
import com.lendbridge.risk.support.AbstractIntegrationTest;
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

/**
 * Covers the opposite arrival order from {@link LoanSubmittedThenKycVerifiedOrderingIT}: the
 * borrower's trust profile already exists by the time {@code loan.submitted} arrives, so the
 * {@code RiskAssessment} row should be graded immediately rather than ever passing through
 * {@code PENDING_KYC}.
 */
class KycVerifiedThenLoanSubmittedOrderingIT extends AbstractIntegrationTest {

  @Test
  void kycVerifiedBeforeLoanSubmitted_gradesImmediately() {
    UUID loanId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();

    publishKycVerified(borrowerUserId, "TIER_3");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(borrowerTrustProfileRepository.findById(borrowerUserId)).isPresent());

    publishLoanSubmitted(loanId, borrowerUserId, 1_000_000L, 12, 1500);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(riskAssessmentRepository.findById(loanId))
                    .get()
                    .extracting(a -> a.getStatus())
                    .isEqualTo(RiskAssessmentStatus.GRADED));

    assertThat(riskAssessmentRepository.findById(loanId).orElseThrow().getGrade()).isNotNull();
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

  private void publishKycVerified(UUID userId, String trustTier) {
    KycVerified payload =
        KycVerified.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(userId.toString())
            .setTrustTier(trustTier)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    publish("kyc.verified", userId.toString(), payload);
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

package com.lendbridge.reporting.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.investment.avro.InvestmentCancelled;
import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.kyc.avro.KycRejected;
import com.lendbridge.kyc.avro.KycVerified;
import com.lendbridge.loan.avro.LoanDelisted;
import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.loan.avro.LoanSubmitted;
import com.lendbridge.reporting.model.InvestmentActivityType;
import com.lendbridge.reporting.model.KycDecisionType;
import com.lendbridge.reporting.model.LoanPipelineStatus;
import com.lendbridge.reporting.support.AbstractIntegrationTest;
import com.lendbridge.risk.avro.RiskGraded;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

/** One event per consumed topic, proving each typed consumer writes its fact table row. */
class ReportingEventConsumptionIT extends AbstractIntegrationTest {

  @Test
  void userRegistered_landsInUserSignupFact() {
    UUID userId = UUID.randomUUID();
    publish(
        "user.registered",
        userId.toString(),
        UserRegistered.newBuilder()
            .setUserId(userId.toString())
            .setEmail("borrower@example.com")
            .setEmailVerified(true)
            .setPhoneVerified(true)
            .setRegisteredAtEpochMillis(System.currentTimeMillis())
            .build());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(userSignupFactRepository.findAll())
                    .anyMatch(f -> f.getUserId().equals(userId)));
  }

  @Test
  void loanLifecycleEvents_landInLoanPipelineFact() {
    UUID loanId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();

    publish(
        "loan.submitted",
        loanId.toString(),
        LoanSubmitted.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(borrowerUserId.toString())
            .setAmountKobo(1_000_000L)
            .setTenorMonths(12)
            .setInterestRateBps(1500)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build());
    publish(
        "loan.listed",
        loanId.toString(),
        LoanListed.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(borrowerUserId.toString())
            .setAmountKobo(1_000_000L)
            .setInterestRateBps(1500)
            .setTenorMonths(12)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build());
    publish(
        "loan.delisted",
        loanId.toString(),
        LoanDelisted.newBuilder().setLoanId(loanId.toString()).setOccurredAtEpochMillis(System.currentTimeMillis()).build());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              var rows = loanPipelineFactRepository.findAll();
              assertThat(rows)
                  .anyMatch(f -> f.getLoanId().equals(loanId) && f.getStatus() == LoanPipelineStatus.SUBMITTED);
              assertThat(rows)
                  .anyMatch(f -> f.getLoanId().equals(loanId) && f.getStatus() == LoanPipelineStatus.LISTED);
              assertThat(rows)
                  .anyMatch(f -> f.getLoanId().equals(loanId) && f.getStatus() == LoanPipelineStatus.DELISTED);
            });
  }

  @Test
  void riskGraded_landsInRiskGradeFact() {
    UUID loanId = UUID.randomUUID();
    publish(
        "risk.graded",
        loanId.toString(),
        RiskGraded.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setGrade("B")
            .setModelVersion("rules-v1")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(riskGradeFactRepository.findAll())
                    .anyMatch(f -> f.getLoanId().equals(loanId) && f.getGrade().equals("B")));
  }

  @Test
  void investmentCommittedAndCancelled_landInInvestmentActivityFact() {
    UUID commitmentId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();

    publish(
        "investment.committed",
        commitmentId.toString(),
        InvestmentCommitted.newBuilder()
            .setCommitmentId(commitmentId.toString())
            .setLoanId(loanId.toString())
            .setLenderUserId(lenderUserId.toString())
            .setAmountKobo(500_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(investmentActivityFactRepository.findAll())
                    .anyMatch(
                        f ->
                            f.getCommitmentId().equals(commitmentId)
                                && f.getActivityType() == InvestmentActivityType.COMMITTED
                                && lenderUserId.equals(f.getLenderUserId())));

    UUID cancelledCommitmentId = UUID.randomUUID();
    publish(
        "investment.cancelled",
        cancelledCommitmentId.toString(),
        InvestmentCancelled.newBuilder()
            .setCommitmentId(cancelledCommitmentId.toString())
            .setLoanId(loanId.toString())
            .setAmountKobo(500_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(investmentActivityFactRepository.findAll())
                    .anyMatch(
                        f ->
                            f.getCommitmentId().equals(cancelledCommitmentId)
                                && f.getActivityType() == InvestmentActivityType.CANCELLED
                                && f.getLenderUserId() == null));
  }

  @Test
  void kycVerifiedAndRejected_landInKycDecisionFact() {
    UUID verifiedUserId = UUID.randomUUID();
    publish(
        "kyc.verified",
        verifiedUserId.toString(),
        KycVerified.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(verifiedUserId.toString())
            .setTrustTier("TIER_2")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build());

    UUID rejectedUserId = UUID.randomUUID();
    publish(
        "kyc.rejected",
        rejectedUserId.toString(),
        KycRejected.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(rejectedUserId.toString())
            .setReason("Document mismatch")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              var rows = kycDecisionFactRepository.findAll();
              assertThat(rows)
                  .anyMatch(f -> f.getUserId().equals(verifiedUserId) && f.getDecision() == KycDecisionType.VERIFIED);
              assertThat(rows)
                  .anyMatch(f -> f.getUserId().equals(rejectedUserId) && f.getDecision() == KycDecisionType.REJECTED);
            });
  }

  private void publish(String topic, String key, Object payload) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    props.put("schema.registry.url", schemaRegistryUrl());

    try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props)) {
      producer.send(new ProducerRecord<>(topic, key, payload)).get();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish test " + topic + " event", e);
    }
  }
}

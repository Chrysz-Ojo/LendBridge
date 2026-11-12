package com.lendbridge.messaging.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.support.AbstractIntegrationTest;
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

class InvestmentCommittedEventConsumptionIT extends AbstractIntegrationTest {

  @Test
  void loanListedThenInvestmentCommitted_opensConversation() {
    UUID loanId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();

    LoanListed loanListed =
        LoanListed.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(borrowerUserId.toString())
            .setAmountKobo(1_000_000L)
            .setInterestRateBps(1500)
            .setTenorMonths(12)
            .setPurpose("WORKING_CAPITAL")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    publish("loan.listed", UUID.randomUUID().toString(), loanId.toString(), loanListed);

    InvestmentCommitted investmentCommitted =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(loanId.toString())
            .setLenderUserId(lenderUserId.toString())
            .setAmountKobo(500_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    publish("investment.committed", UUID.randomUUID().toString(), loanId.toString(), investmentCommitted);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Conversation conversation =
                  conversationRepository.findByLoanIdAndLenderUserId(loanId, lenderUserId).orElseThrow();
              assertThat(conversation.getBorrowerUserId()).isEqualTo(borrowerUserId);
            });
  }

  private void publish(String topic, String eventId, String key, Object payload) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    props.put("schema.registry.url", schemaRegistryUrl());

    try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props)) {
      ProducerRecord<String, Object> record = new ProducerRecord<>(topic, null, key, payload);
      record.headers().add(new RecordHeader("ce_id", eventId.getBytes()));
      producer.send(record).get();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish test " + topic + " event", e);
    }
  }
}

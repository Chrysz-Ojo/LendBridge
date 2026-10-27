package com.lendbridge.wallet.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.starter.outbox.AbstractOutboxRelay;
import com.lendbridge.starter.outbox.CloudEventHeaders;
import com.lendbridge.starter.outbox.OutboxEvent;
import com.lendbridge.starter.outbox.OutboxEventRepository;
import com.lendbridge.wallet.avro.WalletBankAccountVerified;
import com.lendbridge.wallet.avro.WalletCreated;
import com.lendbridge.wallet.avro.WalletHoldCreated;
import com.lendbridge.wallet.avro.WalletHoldReleased;
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
      case KafkaTopics.WALLET_CREATED -> publishWalletCreated(event);
      case KafkaTopics.WALLET_HOLD_CREATED -> publishHoldCreated(event);
      case KafkaTopics.WALLET_HOLD_RELEASED -> publishHoldReleased(event);
      case KafkaTopics.WALLET_BANK_ACCOUNT_VERIFIED -> publishBankAccountVerified(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishWalletCreated(OutboxEvent event) throws Exception {
    WalletCreatedPayload payload = objectMapper.readValue(event.getPayload(), WalletCreatedPayload.class);
    WalletCreated avroValue =
        WalletCreated.newBuilder()
            .setWalletId(payload.getWalletId())
            .setUserId(payload.getUserId())
            .setCurrency(payload.getCurrency())
            .setCreatedAtEpochMillis(payload.getCreatedAtEpochMillis())
            .build();
    send(KafkaTopics.WALLET_CREATED, event, avroValue);
  }

  private void publishHoldCreated(OutboxEvent event) throws Exception {
    WalletHoldCreatedPayload payload =
        objectMapper.readValue(event.getPayload(), WalletHoldCreatedPayload.class);
    WalletHoldCreated avroValue =
        WalletHoldCreated.newBuilder()
            .setHoldId(payload.getHoldId())
            .setWalletId(payload.getWalletId())
            .setUserId(payload.getUserId())
            .setAmountKobo(payload.getAmountKobo())
            .setReference(payload.getReference())
            .setCreatedAtEpochMillis(payload.getCreatedAtEpochMillis())
            .build();
    send(KafkaTopics.WALLET_HOLD_CREATED, event, avroValue);
  }

  private void publishHoldReleased(OutboxEvent event) throws Exception {
    WalletHoldReleasedPayload payload =
        objectMapper.readValue(event.getPayload(), WalletHoldReleasedPayload.class);
    WalletHoldReleased avroValue =
        WalletHoldReleased.newBuilder()
            .setHoldId(payload.getHoldId())
            .setWalletId(payload.getWalletId())
            .setUserId(payload.getUserId())
            .setAmountKobo(payload.getAmountKobo())
            .setReference(payload.getReference())
            .setReleasedAtEpochMillis(payload.getReleasedAtEpochMillis())
            .build();
    send(KafkaTopics.WALLET_HOLD_RELEASED, event, avroValue);
  }

  private void publishBankAccountVerified(OutboxEvent event) throws Exception {
    WalletBankAccountVerifiedPayload payload =
        objectMapper.readValue(event.getPayload(), WalletBankAccountVerifiedPayload.class);
    WalletBankAccountVerified avroValue =
        WalletBankAccountVerified.newBuilder()
            .setBankAccountId(payload.getBankAccountId())
            .setWalletId(payload.getWalletId())
            .setUserId(payload.getUserId())
            .setVerifiedAtEpochMillis(payload.getVerifiedAtEpochMillis())
            .build();
    send(KafkaTopics.WALLET_BANK_ACCOUNT_VERIFIED, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

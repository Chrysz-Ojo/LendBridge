package com.lendbridge.wallet.messaging;

import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.model.ProcessedEvent;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletCreatedPayload;
import com.lendbridge.wallet.repository.ProcessedEventRepository;
import com.lendbridge.wallet.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-provisions a {@link Wallet} row (currency only — no ledger account yet) for every newly
 * registered user; PRD E10-S1: "each user has a wallet," universal, no opt-in. The ledger account
 * is deliberately NOT opened here — there is no service-to-service auth mechanism anywhere in
 * this repo, so a Kafka consumer has no inbound caller JWT to forward to ledger-service; it's
 * provisioned lazily on first authenticated access instead (see {@code
 * WalletServiceImpl.getOrCreateWallet}). Idempotent against redelivery via {@code
 * processed_event}, same pattern as user-profile-service's own consumer of this same topic.
 */
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventConsumer.class);
  private static final String CURRENCY = "NGN";

  private final WalletRepository walletRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final OutboxEventWriter outboxEventWriter;

  @KafkaListener(topics = "user.registered", groupId = "wallet-service")
  @Transactional
  public void onUserRegistered(
      ConsumerRecord<String, UserRegistered> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed user.registered event {}", eventId);
      return;
    }

    UserRegistered payload = record.value();
    UUID userId = UUID.fromString(payload.getUserId());
    if (!walletRepository.existsByUserId(userId)) {
      Instant now = Instant.now();
      UUID walletId = UuidV7.generate();
      walletRepository.save(
          Wallet.builder()
              .id(walletId)
              .userId(userId)
              .currency(CURRENCY)
              .ledgerAccountId(null)
              .createdAt(now)
              .updatedAt(now)
              .build());

      outboxEventWriter.write(
          "Wallet",
          walletId.toString(),
          KafkaTopics.WALLET_CREATED,
          new WalletCreatedPayload(walletId.toString(), userId.toString(), CURRENCY, now.toEpochMilli()));
    }

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}

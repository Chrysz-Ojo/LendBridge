package com.lendbridge.ledger.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.ledger.avro.LedgerLoanDisbursementCompleted;
import com.lendbridge.ledger.avro.LedgerLoanDisbursementFailed;
import com.lendbridge.ledger.avro.LedgerLoanRepaymentCompleted;
import com.lendbridge.ledger.avro.LedgerPosted;
import com.lendbridge.ledger.avro.LedgerPosting;
import com.lendbridge.starter.outbox.AbstractOutboxRelay;
import com.lendbridge.starter.outbox.CloudEventHeaders;
import com.lendbridge.starter.outbox.OutboxEvent;
import com.lendbridge.starter.outbox.OutboxEventRepository;
import java.util.List;
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
      case KafkaTopics.LEDGER_POSTED -> publishLedgerPosted(event);
      case KafkaTopics.LEDGER_LOAN_DISBURSEMENT_COMPLETED -> publishLoanDisbursementCompleted(event);
      case KafkaTopics.LEDGER_LOAN_DISBURSEMENT_FAILED -> publishLoanDisbursementFailed(event);
      case KafkaTopics.LEDGER_LOAN_REPAYMENT_COMPLETED -> publishLoanRepaymentCompleted(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishLoanDisbursementCompleted(OutboxEvent event) throws Exception {
    LedgerLoanDisbursementCompletedPayload payload =
        objectMapper.readValue(event.getPayload(), LedgerLoanDisbursementCompletedPayload.class);
    LedgerLoanDisbursementCompleted avroValue =
        LedgerLoanDisbursementCompleted.newBuilder()
            .setLoanId(payload.getLoanId())
            .setTotalKobo(payload.getTotalKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LEDGER_LOAN_DISBURSEMENT_COMPLETED, event, avroValue);
  }

  private void publishLoanDisbursementFailed(OutboxEvent event) throws Exception {
    LedgerLoanDisbursementFailedPayload payload =
        objectMapper.readValue(event.getPayload(), LedgerLoanDisbursementFailedPayload.class);
    LedgerLoanDisbursementFailed avroValue =
        LedgerLoanDisbursementFailed.newBuilder()
            .setLoanId(payload.getLoanId())
            .setReason(payload.getReason())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LEDGER_LOAN_DISBURSEMENT_FAILED, event, avroValue);
  }

  private void publishLoanRepaymentCompleted(OutboxEvent event) throws Exception {
    LedgerLoanRepaymentCompletedPayload payload =
        objectMapper.readValue(event.getPayload(), LedgerLoanRepaymentCompletedPayload.class);
    LedgerLoanRepaymentCompleted avroValue =
        LedgerLoanRepaymentCompleted.newBuilder()
            .setLoanId(payload.getLoanId())
            .setInstalmentId(payload.getInstalmentId())
            .setSuccess(payload.isSuccess())
            .setReason(payload.getReason())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LEDGER_LOAN_REPAYMENT_COMPLETED, event, avroValue);
  }

  private void publishLedgerPosted(OutboxEvent event) throws Exception {
    LedgerPostedPayload payload = objectMapper.readValue(event.getPayload(), LedgerPostedPayload.class);
    List<LedgerPosting> postings =
        payload.getPostings().stream()
            .map(
                p ->
                    LedgerPosting.newBuilder()
                        .setPostingId(p.getPostingId())
                        .setAccountId(p.getAccountId())
                        .setDirection(p.getDirection())
                        .setAmountKobo(p.getAmountKobo())
                        .build())
            .toList();
    LedgerPosted avroValue =
        LedgerPosted.newBuilder()
            .setJournalEntryId(payload.getJournalEntryId())
            .setIdempotencyKey(payload.getIdempotencyKey())
            .setCurrency(payload.getCurrency())
            .setReference(payload.getReference())
            .setCorrelationId(payload.getCorrelationId())
            .setPostedAtEpochMillis(payload.getPostedAtEpochMillis())
            .setPostings(postings)
            .build();
    send(KafkaTopics.LEDGER_POSTED, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

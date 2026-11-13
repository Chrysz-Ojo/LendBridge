package com.lendbridge.investment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.investment.avro.DisbursementLeg;
import com.lendbridge.investment.avro.InvestmentCancelled;
import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.investment.avro.InvestmentDisbursementCompensate;
import com.lendbridge.investment.avro.InvestmentDisbursementReady;
import com.lendbridge.investment.avro.InvestmentHoldsCaptureRequested;
import com.lendbridge.investment.avro.InvestmentRepaymentFailed;
import com.lendbridge.investment.avro.InvestmentRepaymentReady;
import com.lendbridge.investment.avro.RepaymentLeg;
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
      case KafkaTopics.INVESTMENT_COMMITTED -> publishInvestmentCommitted(event);
      case KafkaTopics.INVESTMENT_CANCELLED -> publishInvestmentCancelled(event);
      case KafkaTopics.INVESTMENT_HOLDS_CAPTURE_REQUESTED -> publishHoldsCaptureRequested(event);
      case KafkaTopics.INVESTMENT_DISBURSEMENT_READY -> publishDisbursementReady(event);
      case KafkaTopics.INVESTMENT_DISBURSEMENT_COMPENSATE -> publishDisbursementCompensate(event);
      case KafkaTopics.INVESTMENT_REPAYMENT_READY -> publishRepaymentReady(event);
      case KafkaTopics.INVESTMENT_REPAYMENT_FAILED -> publishRepaymentFailed(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishInvestmentCommitted(OutboxEvent event) throws Exception {
    InvestmentCommittedPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentCommittedPayload.class);
    InvestmentCommitted avroValue =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(payload.getCommitmentId().toString())
            .setLoanId(payload.getLoanId().toString())
            .setLenderUserId(payload.getLenderUserId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_COMMITTED, event, avroValue);
  }

  private void publishInvestmentCancelled(OutboxEvent event) throws Exception {
    InvestmentCancelledPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentCancelledPayload.class);
    InvestmentCancelled avroValue =
        InvestmentCancelled.newBuilder()
            .setCommitmentId(payload.getCommitmentId().toString())
            .setLoanId(payload.getLoanId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_CANCELLED, event, avroValue);
  }

  private void publishHoldsCaptureRequested(OutboxEvent event) throws Exception {
    InvestmentHoldsCaptureRequestedPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentHoldsCaptureRequestedPayload.class);
    InvestmentHoldsCaptureRequested avroValue =
        InvestmentHoldsCaptureRequested.newBuilder()
            .setLoanId(payload.getLoanId())
            .setHoldIds(payload.getHoldIds())
            .setTotalKobo(payload.getTotalKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_HOLDS_CAPTURE_REQUESTED, event, avroValue);
  }

  private void publishDisbursementReady(OutboxEvent event) throws Exception {
    InvestmentDisbursementReadyPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentDisbursementReadyPayload.class);
    List<DisbursementLeg> legs =
        payload.getLegs().stream()
            .map(
                l ->
                    DisbursementLeg.newBuilder()
                        .setLenderLedgerAccountId(l.getLenderLedgerAccountId())
                        .setAmountKobo(l.getAmountKobo())
                        .build())
            .toList();
    InvestmentDisbursementReady avroValue =
        InvestmentDisbursementReady.newBuilder()
            .setLoanId(payload.getLoanId())
            .setBorrowerLedgerAccountId(payload.getBorrowerLedgerAccountId())
            .setOriginationFeeKobo(payload.getOriginationFeeKobo())
            .setTotalKobo(payload.getTotalKobo())
            .setLegs(legs)
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_DISBURSEMENT_READY, event, avroValue);
  }

  private void publishDisbursementCompensate(OutboxEvent event) throws Exception {
    InvestmentDisbursementCompensatePayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentDisbursementCompensatePayload.class);
    InvestmentDisbursementCompensate avroValue =
        InvestmentDisbursementCompensate.newBuilder()
            .setLoanId(payload.getLoanId())
            .setHoldIds(payload.getHoldIds())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_DISBURSEMENT_COMPENSATE, event, avroValue);
  }

  private void publishRepaymentReady(OutboxEvent event) throws Exception {
    InvestmentRepaymentReadyPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentRepaymentReadyPayload.class);
    List<RepaymentLeg> legs =
        payload.getLegs().stream()
            .map(
                l ->
                    RepaymentLeg.newBuilder()
                        .setLenderLedgerAccountId(l.getLenderLedgerAccountId())
                        .setAmountKobo(l.getAmountKobo())
                        .build())
            .toList();
    InvestmentRepaymentReady avroValue =
        InvestmentRepaymentReady.newBuilder()
            .setLoanId(payload.getLoanId())
            .setInstalmentId(payload.getInstalmentId())
            .setBorrowerLedgerAccountId(payload.getBorrowerLedgerAccountId())
            .setTotalKobo(payload.getTotalKobo())
            .setLegs(legs)
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_REPAYMENT_READY, event, avroValue);
  }

  private void publishRepaymentFailed(OutboxEvent event) throws Exception {
    InvestmentRepaymentFailedPayload payload =
        objectMapper.readValue(event.getPayload(), InvestmentRepaymentFailedPayload.class);
    InvestmentRepaymentFailed avroValue =
        InvestmentRepaymentFailed.newBuilder()
            .setLoanId(payload.getLoanId())
            .setInstalmentId(payload.getInstalmentId())
            .setReason(payload.getReason())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.INVESTMENT_REPAYMENT_FAILED, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

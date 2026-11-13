package com.lendbridge.loan.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.loan.avro.LoanAgreementSigned;
import com.lendbridge.loan.avro.LoanDelisted;
import com.lendbridge.loan.avro.LoanDisbursed;
import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.loan.avro.LoanRepaid;
import com.lendbridge.loan.avro.LoanRepaymentRequested;
import com.lendbridge.loan.avro.LoanSubmitted;
import com.lendbridge.starter.outbox.AbstractOutboxRelay;
import com.lendbridge.starter.outbox.CloudEventHeaders;
import com.lendbridge.starter.outbox.OutboxEvent;
import com.lendbridge.starter.outbox.OutboxEventRepository;
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
      case KafkaTopics.LOAN_SUBMITTED -> publishLoanSubmitted(event);
      case KafkaTopics.LOAN_LISTED -> publishLoanListed(event);
      case KafkaTopics.LOAN_DELISTED -> publishLoanDelisted(event);
      case KafkaTopics.LOAN_AGREEMENT_SIGNED -> publishLoanAgreementSigned(event);
      case KafkaTopics.LOAN_DISBURSED -> publishLoanDisbursed(event);
      case KafkaTopics.LOAN_REPAYMENT_REQUESTED -> publishLoanRepaymentRequested(event);
      case KafkaTopics.LOAN_REPAID -> publishLoanRepaid(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishLoanSubmitted(OutboxEvent event) throws Exception {
    LoanSubmittedPayload payload = objectMapper.readValue(event.getPayload(), LoanSubmittedPayload.class);
    LoanSubmitted avroValue =
        LoanSubmitted.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_SUBMITTED, event, avroValue);
  }

  private void publishLoanListed(OutboxEvent event) throws Exception {
    LoanListedPayload payload = objectMapper.readValue(event.getPayload(), LoanListedPayload.class);
    LoanListed avroValue =
        LoanListed.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setInterestRateBps(payload.getInterestRateBps())
            .setTenorMonths(payload.getTenorMonths())
            .setPurpose(payload.getPurpose())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_LISTED, event, avroValue);
  }

  private void publishLoanDelisted(OutboxEvent event) throws Exception {
    LoanDelistedPayload payload = objectMapper.readValue(event.getPayload(), LoanDelistedPayload.class);
    LoanDelisted avroValue =
        LoanDelisted.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_DELISTED, event, avroValue);
  }

  private void publishLoanAgreementSigned(OutboxEvent event) throws Exception {
    LoanAgreementSignedPayload payload =
        objectMapper.readValue(event.getPayload(), LoanAgreementSignedPayload.class);
    LoanAgreementSigned avroValue =
        LoanAgreementSigned.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setOriginationFeeKobo(payload.getOriginationFeeKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_AGREEMENT_SIGNED, event, avroValue);
  }

  private void publishLoanDisbursed(OutboxEvent event) throws Exception {
    LoanDisbursedPayload payload = objectMapper.readValue(event.getPayload(), LoanDisbursedPayload.class);
    LoanDisbursed avroValue =
        LoanDisbursed.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_DISBURSED, event, avroValue);
  }

  private void publishLoanRepaymentRequested(OutboxEvent event) throws Exception {
    LoanRepaymentRequestedPayload payload =
        objectMapper.readValue(event.getPayload(), LoanRepaymentRequestedPayload.class);
    LoanRepaymentRequested avroValue =
        LoanRepaymentRequested.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setInstalmentId(payload.getInstalmentId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setAmountKobo(payload.getAmountKobo())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_REPAYMENT_REQUESTED, event, avroValue);
  }

  private void publishLoanRepaid(OutboxEvent event) throws Exception {
    LoanRepaidPayload payload = objectMapper.readValue(event.getPayload(), LoanRepaidPayload.class);
    LoanRepaid avroValue =
        LoanRepaid.newBuilder()
            .setLoanId(payload.getLoanId().toString())
            .setBorrowerUserId(payload.getBorrowerUserId().toString())
            .setOccurredAtEpochMillis(payload.getOccurredAtEpochMillis())
            .build();
    send(KafkaTopics.LOAN_REPAID, event, avroValue);
  }

  private void send(String topic, OutboxEvent event, Object avroValue) throws Exception {
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(topic, null, event.getAggregateId(), avroValue, cloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}

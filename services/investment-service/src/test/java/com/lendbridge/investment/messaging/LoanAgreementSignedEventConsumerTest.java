package com.lendbridge.investment.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanAgreementSigned;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanAgreementSignedEventConsumerTest {

  @Mock private DisbursementAssembler disbursementAssembler;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LoanAgreementSignedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanAgreementSignedEventConsumer(disbursementAssembler, processedEventRepository);
  }

  @Test
  void delegatesToAssembler_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    LoanAgreementSigned payload =
        LoanAgreementSigned.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(600_000L)
            .setOriginationFeeKobo(12_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanAgreementSigned> record =
        new ConsumerRecord<>("loan.agreement.signed", 0, 0L, loanId.toString(), payload);

    consumer.onSigned(record, "event-1".getBytes());

    verify(disbursementAssembler).onAgreementSigned(loanId, 12_000L);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    LoanAgreementSigned payload =
        LoanAgreementSigned.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(600_000L)
            .setOriginationFeeKobo(12_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanAgreementSigned> record =
        new ConsumerRecord<>("loan.agreement.signed", 0, 0L, "key", payload);

    consumer.onSigned(record, "event-2".getBytes());

    verifyNoInteractions(disbursementAssembler);
    verify(processedEventRepository, never()).save(any());
  }
}

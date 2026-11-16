package com.lendbridge.investment.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.ledger.avro.LedgerLoanDisbursementFailed;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerLoanDisbursementFailedEventConsumerTest {

  @Mock private DisbursementAssembler disbursementAssembler;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LedgerLoanDisbursementFailedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LedgerLoanDisbursementFailedEventConsumer(disbursementAssembler, processedEventRepository);
  }

  @Test
  void delegatesToAssembler_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    LedgerLoanDisbursementFailed payload =
        LedgerLoanDisbursementFailed.newBuilder()
            .setLoanId(loanId.toString())
            .setReason("insufficient balance")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LedgerLoanDisbursementFailed> record =
        new ConsumerRecord<>("ledger.loan-disbursement.failed", 0, 0L, loanId.toString(), payload);

    consumer.onFailed(record, "event-1".getBytes());

    verify(disbursementAssembler).onLedgerDisbursementFailed(loanId);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    LedgerLoanDisbursementFailed payload =
        LedgerLoanDisbursementFailed.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setReason("insufficient balance")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LedgerLoanDisbursementFailed> record =
        new ConsumerRecord<>("ledger.loan-disbursement.failed", 0, 0L, "key", payload);

    consumer.onFailed(record, "event-2".getBytes());

    verifyNoInteractions(disbursementAssembler);
  }
}

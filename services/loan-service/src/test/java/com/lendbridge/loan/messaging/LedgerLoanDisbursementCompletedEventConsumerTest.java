package com.lendbridge.loan.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.ledger.avro.LedgerLoanDisbursementCompleted;
import com.lendbridge.loan.repository.ProcessedEventRepository;
import com.lendbridge.loan.service.LoanService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerLoanDisbursementCompletedEventConsumerTest {

  @Mock private LoanService loanService;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LedgerLoanDisbursementCompletedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LedgerLoanDisbursementCompletedEventConsumer(loanService, processedEventRepository);
  }

  @Test
  void delegatesToLoanService_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    LedgerLoanDisbursementCompleted payload =
        LedgerLoanDisbursementCompleted.newBuilder()
            .setLoanId(loanId.toString())
            .setTotalKobo(1_000_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LedgerLoanDisbursementCompleted> record =
        new ConsumerRecord<>("ledger.loan-disbursement.completed", 0, 0L, loanId.toString(), payload);

    consumer.onCompleted(record, "event-1".getBytes());

    verify(loanService).disburse(loanId);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    LedgerLoanDisbursementCompleted payload =
        LedgerLoanDisbursementCompleted.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setTotalKobo(1_000_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LedgerLoanDisbursementCompleted> record =
        new ConsumerRecord<>("ledger.loan-disbursement.completed", 0, 0L, "key", payload);

    consumer.onCompleted(record, "event-2".getBytes());

    verifyNoInteractions(loanService);
  }
}

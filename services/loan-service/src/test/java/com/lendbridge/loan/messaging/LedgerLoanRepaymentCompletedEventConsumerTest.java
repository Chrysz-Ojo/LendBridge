package com.lendbridge.loan.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.ledger.avro.LedgerLoanRepaymentCompleted;
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
class LedgerLoanRepaymentCompletedEventConsumerTest {

  @Mock private LoanService loanService;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LedgerLoanRepaymentCompletedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LedgerLoanRepaymentCompletedEventConsumer(loanService, processedEventRepository);
  }

  private static ConsumerRecord<String, LedgerLoanRepaymentCompleted> recordWith(
      UUID loanId, UUID instalmentId, boolean success) {
    LedgerLoanRepaymentCompleted payload =
        LedgerLoanRepaymentCompleted.newBuilder()
            .setLoanId(loanId.toString())
            .setInstalmentId(instalmentId.toString())
            .setSuccess(success)
            .setReason(success ? null : "insufficient balance")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    return new ConsumerRecord<>("ledger.loan-repayment.completed", 0, 0L, loanId.toString(), payload);
  }

  @Test
  void success_marksInstalmentPaid() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID instalmentId = UUID.randomUUID();

    consumer.onCompleted(recordWith(loanId, instalmentId, true), "event-1".getBytes());

    verify(loanService).markInstalmentPaid(loanId, instalmentId);
    verify(loanService, never()).revertInstalmentToPending(any(), any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void failure_revertsInstalmentToPending() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID instalmentId = UUID.randomUUID();

    consumer.onCompleted(recordWith(loanId, instalmentId, false), "event-2".getBytes());

    verify(loanService).revertInstalmentToPending(loanId, instalmentId);
    verify(loanService, never()).markInstalmentPaid(any(), any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);

    consumer.onCompleted(recordWith(UUID.randomUUID(), UUID.randomUUID(), true), "event-3".getBytes());

    verifyNoInteractions(loanService);
  }
}

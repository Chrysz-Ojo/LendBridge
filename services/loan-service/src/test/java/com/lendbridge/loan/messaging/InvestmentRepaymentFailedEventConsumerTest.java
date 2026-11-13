package com.lendbridge.loan.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentRepaymentFailed;
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
class InvestmentRepaymentFailedEventConsumerTest {

  @Mock private LoanService loanService;
  @Mock private ProcessedEventRepository processedEventRepository;

  private InvestmentRepaymentFailedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvestmentRepaymentFailedEventConsumer(loanService, processedEventRepository);
  }

  @Test
  void delegatesToLoanService_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID instalmentId = UUID.randomUUID();
    InvestmentRepaymentFailed payload =
        InvestmentRepaymentFailed.newBuilder()
            .setLoanId(loanId.toString())
            .setInstalmentId(instalmentId.toString())
            .setReason("borrower wallet not provisioned")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentRepaymentFailed> record =
        new ConsumerRecord<>("investment.repayment.failed", 0, 0L, loanId.toString(), payload);

    consumer.onFailed(record, "event-1".getBytes());

    verify(loanService).revertInstalmentToPending(loanId, instalmentId);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    InvestmentRepaymentFailed payload =
        InvestmentRepaymentFailed.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setInstalmentId(UUID.randomUUID().toString())
            .setReason("reason")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentRepaymentFailed> record =
        new ConsumerRecord<>("investment.repayment.failed", 0, 0L, "key", payload);

    consumer.onFailed(record, "event-2".getBytes());

    verifyNoInteractions(loanService);
  }
}

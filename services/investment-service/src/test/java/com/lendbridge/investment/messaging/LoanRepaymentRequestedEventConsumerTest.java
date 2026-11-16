package com.lendbridge.investment.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.fundingsaga.RepaymentAssembler;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanRepaymentRequested;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanRepaymentRequestedEventConsumerTest {

  @Mock private RepaymentAssembler repaymentAssembler;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LoanRepaymentRequestedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanRepaymentRequestedEventConsumer(repaymentAssembler, processedEventRepository);
  }

  @Test
  void delegatesToAssembler_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID instalmentId = UUID.randomUUID();
    LoanRepaymentRequested payload =
        LoanRepaymentRequested.newBuilder()
            .setLoanId(loanId.toString())
            .setInstalmentId(instalmentId.toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(85_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanRepaymentRequested> record =
        new ConsumerRecord<>("loan.repayment.requested", 0, 0L, loanId.toString(), payload);

    consumer.onRepaymentRequested(record, "event-1".getBytes());

    verify(repaymentAssembler).onRepaymentRequested(instalmentId, loanId, 85_000L);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    LoanRepaymentRequested payload =
        LoanRepaymentRequested.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setInstalmentId(UUID.randomUUID().toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(85_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanRepaymentRequested> record =
        new ConsumerRecord<>("loan.repayment.requested", 0, 0L, "key", payload);

    consumer.onRepaymentRequested(record, "event-2".getBytes());

    verifyNoInteractions(repaymentAssembler);
  }
}

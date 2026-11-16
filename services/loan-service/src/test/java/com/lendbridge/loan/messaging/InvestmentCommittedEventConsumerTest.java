package com.lendbridge.loan.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentCommitted;
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
class InvestmentCommittedEventConsumerTest {

  @Mock private LoanService loanService;
  @Mock private ProcessedEventRepository processedEventRepository;

  private InvestmentCommittedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvestmentCommittedEventConsumer(loanService, processedEventRepository);
  }

  @Test
  void delegatesToLoanService_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    InvestmentCommitted payload =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(loanId.toString())
            .setLenderUserId(UUID.randomUUID().toString())
            .setAmountKobo(400_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCommitted> record =
        new ConsumerRecord<>("investment.committed", 0, 0L, loanId.toString(), payload);

    consumer.onCommitted(record, "event-1".getBytes());

    verify(loanService).recordCommitment(loanId, 400_000L);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    InvestmentCommitted payload =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(UUID.randomUUID().toString())
            .setLenderUserId(UUID.randomUUID().toString())
            .setAmountKobo(400_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCommitted> record =
        new ConsumerRecord<>("investment.committed", 0, 0L, "key", payload);

    consumer.onCommitted(record, "event-2".getBytes());

    verifyNoInteractions(loanService);
  }
}

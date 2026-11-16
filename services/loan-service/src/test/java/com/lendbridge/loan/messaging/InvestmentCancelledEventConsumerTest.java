package com.lendbridge.loan.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentCancelled;
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
class InvestmentCancelledEventConsumerTest {

  @Mock private LoanService loanService;
  @Mock private ProcessedEventRepository processedEventRepository;

  private InvestmentCancelledEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvestmentCancelledEventConsumer(loanService, processedEventRepository);
  }

  @Test
  void delegatesToLoanService_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    InvestmentCancelled payload =
        InvestmentCancelled.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(loanId.toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCancelled> record =
        new ConsumerRecord<>("investment.cancelled", 0, 0L, loanId.toString(), payload);

    consumer.onCancelled(record, "event-1".getBytes());

    verify(loanService).recordCommitmentCancelled(loanId, 300_000L);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    InvestmentCancelled payload =
        InvestmentCancelled.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(UUID.randomUUID().toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCancelled> record =
        new ConsumerRecord<>("investment.cancelled", 0, 0L, "key", payload);

    consumer.onCancelled(record, "event-2".getBytes());

    verifyNoInteractions(loanService);
  }
}

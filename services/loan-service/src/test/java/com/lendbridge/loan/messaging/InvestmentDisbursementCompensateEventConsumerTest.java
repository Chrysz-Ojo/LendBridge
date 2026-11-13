package com.lendbridge.loan.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentDisbursementCompensate;
import com.lendbridge.loan.repository.ProcessedEventRepository;
import com.lendbridge.loan.service.LoanService;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentDisbursementCompensateEventConsumerTest {

  @Mock private LoanService loanService;
  @Mock private ProcessedEventRepository processedEventRepository;

  private InvestmentDisbursementCompensateEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvestmentDisbursementCompensateEventConsumer(loanService, processedEventRepository);
  }

  @Test
  void delegatesToLoanService_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    InvestmentDisbursementCompensate payload =
        InvestmentDisbursementCompensate.newBuilder()
            .setLoanId(loanId.toString())
            .setHoldIds(List.of(UUID.randomUUID().toString()))
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentDisbursementCompensate> record =
        new ConsumerRecord<>("investment.disbursement.compensate", 0, 0L, loanId.toString(), payload);

    consumer.onCompensate(record, "event-1".getBytes());

    verify(loanService).revertToFunded(loanId);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    InvestmentDisbursementCompensate payload =
        InvestmentDisbursementCompensate.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setHoldIds(List.of())
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentDisbursementCompensate> record =
        new ConsumerRecord<>("investment.disbursement.compensate", 0, 0L, "key", payload);

    consumer.onCompensate(record, "event-2".getBytes());

    verifyNoInteractions(loanService);
  }
}

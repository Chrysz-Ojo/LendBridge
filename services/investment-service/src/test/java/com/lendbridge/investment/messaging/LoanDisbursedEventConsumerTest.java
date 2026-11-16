package com.lendbridge.investment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.Holding;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.HoldingRepository;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanDisbursed;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanDisbursedEventConsumerTest {

  @Mock private CommitmentRepository commitmentRepository;
  @Mock private HoldingRepository holdingRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LoanDisbursedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new LoanDisbursedEventConsumer(commitmentRepository, holdingRepository, processedEventRepository);
  }

  @Test
  void createsOneHoldingPerActiveCommitment() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    Commitment commitment =
        Commitment.builder()
            .id(UUID.randomUUID())
            .lenderUserId(lenderUserId)
            .loanId(loanId)
            .amountKobo(600_000L)
            .status(CommitmentStatus.ACTIVE)
            .build();
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE))
        .thenReturn(List.of(commitment));
    LoanDisbursed payload =
        LoanDisbursed.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(600_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanDisbursed> record =
        new ConsumerRecord<>("loan.disbursed", 0, 0L, loanId.toString(), payload);

    consumer.onDisbursed(record, "event-1".getBytes());

    ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
    verify(holdingRepository).save(captor.capture());
    assertThat(captor.getValue().getLenderUserId()).isEqualTo(lenderUserId);
    assertThat(captor.getValue().getLoanId()).isEqualTo(loanId);
    assertThat(captor.getValue().getAmountKobo()).isEqualTo(600_000L);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    LoanDisbursed payload =
        LoanDisbursed.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(600_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanDisbursed> record = new ConsumerRecord<>("loan.disbursed", 0, 0L, "key", payload);

    consumer.onDisbursed(record, "event-2".getBytes());

    verify(holdingRepository, never()).save(any());
    verify(processedEventRepository, never()).save(any());
  }
}

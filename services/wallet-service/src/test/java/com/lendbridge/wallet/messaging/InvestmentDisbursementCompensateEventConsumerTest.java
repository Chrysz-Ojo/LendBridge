package com.lendbridge.wallet.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentDisbursementCompensate;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletHoldsRestoredPayload;
import com.lendbridge.wallet.repository.ProcessedEventRepository;
import com.lendbridge.wallet.service.HoldService;
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
class InvestmentDisbursementCompensateEventConsumerTest {

  @Mock private HoldService holdService;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private OutboxEventWriter outboxEventWriter;

  private InvestmentDisbursementCompensateEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new InvestmentDisbursementCompensateEventConsumer(
            holdService, processedEventRepository, outboxEventWriter);
  }

  private static ConsumerRecord<String, InvestmentDisbursementCompensate> recordWith(
      String loanId, List<String> holdIds) {
    InvestmentDisbursementCompensate payload =
        InvestmentDisbursementCompensate.newBuilder()
            .setLoanId(loanId)
            .setHoldIds(holdIds)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    return new ConsumerRecord<>("investment.disbursement.compensate", 0, 0L, loanId, payload);
  }

  @Test
  void restoresHolds_andPublishesRestored() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    String loanId = UUID.randomUUID().toString();
    List<String> holdIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    consumer.onCompensate(recordWith(loanId, holdIds), "event-1".getBytes());

    verify(holdService).restoreHolds(holdIds.stream().map(UUID::fromString).toList());
    ArgumentCaptor<WalletHoldsRestoredPayload> captor = ArgumentCaptor.forClass(WalletHoldsRestoredPayload.class);
    verify(outboxEventWriter).write(eq("Loan"), eq(loanId), eq(KafkaTopics.WALLET_HOLDS_RESTORED), captor.capture());
    assertThat(captor.getValue().getHoldIds()).containsExactlyElementsOf(holdIds);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);

    consumer.onCompensate(
        recordWith(UUID.randomUUID().toString(), List.of(UUID.randomUUID().toString())),
        "event-2".getBytes());

    verify(holdService, never()).restoreHolds(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
    verify(processedEventRepository, never()).save(any());
  }
}

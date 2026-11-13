package com.lendbridge.wallet.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentHoldsCaptureRequested;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletHoldsCaptureAssembledPayload;
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
class InvestmentHoldsCaptureRequestedEventConsumerTest {

  @Mock private HoldService holdService;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private OutboxEventWriter outboxEventWriter;

  private InvestmentHoldsCaptureRequestedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new InvestmentHoldsCaptureRequestedEventConsumer(
            holdService, processedEventRepository, outboxEventWriter);
  }

  private static ConsumerRecord<String, InvestmentHoldsCaptureRequested> recordWith(
      String loanId, List<String> holdIds) {
    InvestmentHoldsCaptureRequested payload =
        InvestmentHoldsCaptureRequested.newBuilder()
            .setLoanId(loanId)
            .setHoldIds(holdIds)
            .setTotalKobo(100_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    return new ConsumerRecord<>("investment.holds.capture-requested", 0, 0L, loanId, payload);
  }

  @Test
  void captureSucceeds_publishesAssembledSuccess() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    String loanId = UUID.randomUUID().toString();
    List<String> holdIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    consumer.onCaptureRequested(recordWith(loanId, holdIds), "event-1".getBytes());

    ArgumentCaptor<WalletHoldsCaptureAssembledPayload> captor =
        ArgumentCaptor.forClass(WalletHoldsCaptureAssembledPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId), eq(KafkaTopics.WALLET_HOLDS_CAPTURE_ASSEMBLED), captor.capture());
    WalletHoldsCaptureAssembledPayload payload = captor.getValue();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getHoldIds()).containsExactlyElementsOf(holdIds);
    assertThat(payload.getFailureReason()).isNull();
    verify(holdService).captureHolds(holdIds.stream().map(UUID::fromString).toList());
    verify(processedEventRepository).save(any());
  }

  @Test
  void captureFails_publishesAssembledFailure() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    String loanId = UUID.randomUUID().toString();
    List<String> holdIds = List.of(UUID.randomUUID().toString());
    doThrow(new UnprocessableEntityException("HOLD_NOT_ACTIVE", "not active"))
        .when(holdService)
        .captureHolds(any());

    consumer.onCaptureRequested(recordWith(loanId, holdIds), "event-2".getBytes());

    ArgumentCaptor<WalletHoldsCaptureAssembledPayload> captor =
        ArgumentCaptor.forClass(WalletHoldsCaptureAssembledPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId), eq(KafkaTopics.WALLET_HOLDS_CAPTURE_ASSEMBLED), captor.capture());
    WalletHoldsCaptureAssembledPayload payload = captor.getValue();
    assertThat(payload.isSuccess()).isFalse();
    assertThat(payload.getFailureReason()).isEqualTo("not active");
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);

    consumer.onCaptureRequested(
        recordWith(UUID.randomUUID().toString(), List.of(UUID.randomUUID().toString())),
        "event-3".getBytes());

    verify(holdService, never()).captureHolds(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
    verify(processedEventRepository, never()).save(any());
  }
}

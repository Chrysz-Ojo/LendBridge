package com.lendbridge.investment.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.wallet.avro.WalletHoldsCaptureAssembled;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletHoldsCaptureAssembledEventConsumerTest {

  @Mock private DisbursementAssembler disbursementAssembler;
  @Mock private ProcessedEventRepository processedEventRepository;

  private WalletHoldsCaptureAssembledEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new WalletHoldsCaptureAssembledEventConsumer(disbursementAssembler, processedEventRepository);
  }

  @Test
  void delegatesToAssembler_andMarksProcessed() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    WalletHoldsCaptureAssembled payload =
        WalletHoldsCaptureAssembled.newBuilder()
            .setLoanId(loanId.toString())
            .setSuccess(true)
            .setHoldIds(List.of(UUID.randomUUID().toString()))
            .setFailureReason(null)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, WalletHoldsCaptureAssembled> record =
        new ConsumerRecord<>("wallet.holds.capture-assembled", 0, 0L, loanId.toString(), payload);

    consumer.onCaptureAssembled(record, "event-1".getBytes());

    verify(disbursementAssembler).onHoldsCaptureAssembled(loanId, true, null);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    WalletHoldsCaptureAssembled payload =
        WalletHoldsCaptureAssembled.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setSuccess(true)
            .setHoldIds(List.of())
            .setFailureReason(null)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, WalletHoldsCaptureAssembled> record =
        new ConsumerRecord<>("wallet.holds.capture-assembled", 0, 0L, "key", payload);

    consumer.onCaptureAssembled(record, "event-2".getBytes());

    verifyNoInteractions(disbursementAssembler);
    verify(processedEventRepository, org.mockito.Mockito.never()).save(any());
  }
}

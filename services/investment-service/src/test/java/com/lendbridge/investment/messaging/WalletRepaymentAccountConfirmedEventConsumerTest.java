package com.lendbridge.investment.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.fundingsaga.RepaymentAssembler;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.wallet.avro.WalletRepaymentAccountConfirmed;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletRepaymentAccountConfirmedEventConsumerTest {

  @Mock private RepaymentAssembler repaymentAssembler;
  @Mock private ProcessedEventRepository processedEventRepository;

  private WalletRepaymentAccountConfirmedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new WalletRepaymentAccountConfirmedEventConsumer(repaymentAssembler, processedEventRepository);
  }

  @Test
  void success_delegatesWithParsedLedgerAccountId() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID instalmentId = UUID.randomUUID();
    UUID borrowerLedgerAccountId = UUID.randomUUID();
    WalletRepaymentAccountConfirmed payload =
        WalletRepaymentAccountConfirmed.newBuilder()
            .setLoanId(loanId.toString())
            .setInstalmentId(instalmentId.toString())
            .setSuccess(true)
            .setBorrowerLedgerAccountId(borrowerLedgerAccountId.toString())
            .setFailureReason(null)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, WalletRepaymentAccountConfirmed> record =
        new ConsumerRecord<>("wallet.repayment-account.confirmed", 0, 0L, loanId.toString(), payload);

    consumer.onAccountConfirmed(record, "event-1".getBytes());

    verify(repaymentAssembler).onAccountConfirmed(instalmentId, loanId, true, borrowerLedgerAccountId, null);
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    WalletRepaymentAccountConfirmed payload =
        WalletRepaymentAccountConfirmed.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setInstalmentId(UUID.randomUUID().toString())
            .setSuccess(true)
            .setBorrowerLedgerAccountId(UUID.randomUUID().toString())
            .setFailureReason(null)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, WalletRepaymentAccountConfirmed> record =
        new ConsumerRecord<>("wallet.repayment-account.confirmed", 0, 0L, "key", payload);

    consumer.onAccountConfirmed(record, "event-2".getBytes());

    verifyNoInteractions(repaymentAssembler);
  }
}

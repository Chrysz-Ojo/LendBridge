package com.lendbridge.investment.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.wallet.avro.WalletBorrowerAccountAssembled;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletBorrowerAccountAssembledEventConsumerTest {

  @Mock private DisbursementAssembler disbursementAssembler;
  @Mock private ProcessedEventRepository processedEventRepository;

  private WalletBorrowerAccountAssembledEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new WalletBorrowerAccountAssembledEventConsumer(disbursementAssembler, processedEventRepository);
  }

  @Test
  void success_delegatesWithParsedLedgerAccountId() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID borrowerLedgerAccountId = UUID.randomUUID();
    WalletBorrowerAccountAssembled payload =
        WalletBorrowerAccountAssembled.newBuilder()
            .setLoanId(loanId.toString())
            .setSuccess(true)
            .setBorrowerLedgerAccountId(borrowerLedgerAccountId.toString())
            .setFailureReason(null)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, WalletBorrowerAccountAssembled> record =
        new ConsumerRecord<>("wallet.borrower-account.assembled", 0, 0L, loanId.toString(), payload);

    consumer.onBorrowerAccountAssembled(record, "event-1".getBytes());

    verify(disbursementAssembler).onBorrowerAccountAssembled(loanId, true, borrowerLedgerAccountId, null);
    verify(processedEventRepository).save(any());
  }

  @Test
  void failure_delegatesWithNullLedgerAccountId() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    WalletBorrowerAccountAssembled payload =
        WalletBorrowerAccountAssembled.newBuilder()
            .setLoanId(loanId.toString())
            .setSuccess(false)
            .setBorrowerLedgerAccountId(null)
            .setFailureReason("ledger unavailable")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, WalletBorrowerAccountAssembled> record =
        new ConsumerRecord<>("wallet.borrower-account.assembled", 0, 0L, loanId.toString(), payload);

    consumer.onBorrowerAccountAssembled(record, "event-2".getBytes());

    verify(disbursementAssembler).onBorrowerAccountAssembled(loanId, false, null, "ledger unavailable");
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);
    WalletBorrowerAccountAssembled payload =
        WalletBorrowerAccountAssembled.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setSuccess(true)
            .setBorrowerLedgerAccountId(UUID.randomUUID().toString())
            .setFailureReason(null)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, WalletBorrowerAccountAssembled> record =
        new ConsumerRecord<>("wallet.borrower-account.assembled", 0, 0L, "key", payload);

    consumer.onBorrowerAccountAssembled(record, "event-3".getBytes());

    verifyNoInteractions(disbursementAssembler);
    verify(processedEventRepository, org.mockito.Mockito.never()).save(any());
  }
}

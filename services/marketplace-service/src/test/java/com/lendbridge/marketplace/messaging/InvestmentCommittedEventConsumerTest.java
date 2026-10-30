package com.lendbridge.marketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.marketplace.model.Listing;
import com.lendbridge.marketplace.model.ListingStatus;
import com.lendbridge.marketplace.repository.ListingRepository;
import com.lendbridge.marketplace.repository.ProcessedEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentCommittedEventConsumerTest {

  @Mock private ListingRepository listingRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  private InvestmentCommittedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvestmentCommittedEventConsumer(listingRepository, processedEventRepository);
  }

  private Listing listing(UUID loanId, long fundedSoFar) {
    Instant now = Instant.now();
    return Listing.builder()
        .id(loanId)
        .borrowerUserId(UUID.randomUUID())
        .amountKobo(1_000_000L)
        .interestRateBps(1800)
        .tenorMonths(6)
        .purpose("BUSINESS")
        .status(ListingStatus.ACTIVE)
        .fundedAmountKobo(fundedSoFar)
        .listedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Test
  void onInvestmentCommitted_existingListing_addsToFundedAmount() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    Listing listing = listing(loanId, 200_000L);
    when(listingRepository.findById(loanId)).thenReturn(Optional.of(listing));
    InvestmentCommitted payload =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(loanId.toString())
            .setLenderUserId(UUID.randomUUID().toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCommitted> record =
        new ConsumerRecord<>("investment.committed", 0, 0L, loanId.toString(), payload);

    consumer.onInvestmentCommitted(record, "event-1".getBytes());

    ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
    verify(listingRepository).save(captor.capture());
    assertThat(captor.getValue().getFundedAmountKobo()).isEqualTo(500_000L);
    verify(processedEventRepository).save(any());
  }

  @Test
  void onInvestmentCommitted_noMatchingListing_isANoOp() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    when(listingRepository.findById(loanId)).thenReturn(Optional.empty());
    InvestmentCommitted payload =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(loanId.toString())
            .setLenderUserId(UUID.randomUUID().toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCommitted> record =
        new ConsumerRecord<>("investment.committed", 0, 0L, loanId.toString(), payload);

    consumer.onInvestmentCommitted(record, "event-2".getBytes());

    verify(listingRepository, never()).save(any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void onInvestmentCommitted_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);
    InvestmentCommitted payload =
        InvestmentCommitted.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(UUID.randomUUID().toString())
            .setLenderUserId(UUID.randomUUID().toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCommitted> record = new ConsumerRecord<>("investment.committed", 0, 0L, "key", payload);

    consumer.onInvestmentCommitted(record, "event-3".getBytes());

    verify(listingRepository, never()).findById(any());
    verify(processedEventRepository, never()).save(any());
  }
}

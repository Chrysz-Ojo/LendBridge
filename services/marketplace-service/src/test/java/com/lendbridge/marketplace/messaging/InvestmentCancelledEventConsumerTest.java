package com.lendbridge.marketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.avro.InvestmentCancelled;
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
class InvestmentCancelledEventConsumerTest {

  @Mock private ListingRepository listingRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  private InvestmentCancelledEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new InvestmentCancelledEventConsumer(listingRepository, processedEventRepository);
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
  void onInvestmentCancelled_existingListing_subtractsFromFundedAmount() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    Listing listing = listing(loanId, 500_000L);
    when(listingRepository.findById(loanId)).thenReturn(Optional.of(listing));
    InvestmentCancelled payload =
        InvestmentCancelled.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(loanId.toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCancelled> record =
        new ConsumerRecord<>("investment.cancelled", 0, 0L, loanId.toString(), payload);

    consumer.onInvestmentCancelled(record, "event-1".getBytes());

    ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
    verify(listingRepository).save(captor.capture());
    assertThat(captor.getValue().getFundedAmountKobo()).isEqualTo(200_000L);
    verify(processedEventRepository).save(any());
  }

  @Test
  void onInvestmentCancelled_neverGoesBelowZero() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    Listing listing = listing(loanId, 100_000L);
    when(listingRepository.findById(loanId)).thenReturn(Optional.of(listing));
    InvestmentCancelled payload =
        InvestmentCancelled.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(loanId.toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCancelled> record =
        new ConsumerRecord<>("investment.cancelled", 0, 0L, loanId.toString(), payload);

    consumer.onInvestmentCancelled(record, "event-2".getBytes());

    ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
    verify(listingRepository).save(captor.capture());
    assertThat(captor.getValue().getFundedAmountKobo()).isZero();
  }

  @Test
  void onInvestmentCancelled_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);
    InvestmentCancelled payload =
        InvestmentCancelled.newBuilder()
            .setCommitmentId(UUID.randomUUID().toString())
            .setLoanId(UUID.randomUUID().toString())
            .setAmountKobo(300_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, InvestmentCancelled> record = new ConsumerRecord<>("investment.cancelled", 0, 0L, "key", payload);

    consumer.onInvestmentCancelled(record, "event-3".getBytes());

    verify(listingRepository, never()).findById(any());
    verify(processedEventRepository, never()).save(any());
  }
}

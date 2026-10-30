package com.lendbridge.marketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.marketplace.model.Listing;
import com.lendbridge.marketplace.repository.ListingRepository;
import com.lendbridge.marketplace.repository.ProcessedEventRepository;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanListedEventConsumerTest {

  @Mock private ListingRepository listingRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LoanListedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanListedEventConsumer(listingRepository, processedEventRepository);
  }

  @Test
  void onLoanListed_newEvent_upsertsAnActiveListing() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID borrowerId = UUID.randomUUID();
    LoanListed payload =
        LoanListed.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(borrowerId.toString())
            .setAmountKobo(300_000L)
            .setInterestRateBps(1800)
            .setTenorMonths(6)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanListed> record = new ConsumerRecord<>("loan.listed", 0, 0L, loanId.toString(), payload);

    consumer.onLoanListed(record, "event-1".getBytes());

    ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
    verify(listingRepository).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(loanId);
    assertThat(captor.getValue().getAmountKobo()).isEqualTo(300_000L);
    assertThat(captor.getValue().getPurpose()).isEqualTo("BUSINESS");
    verify(processedEventRepository).save(any());
  }

  @Test
  void onLoanListed_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    LoanListed payload =
        LoanListed.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(300_000L)
            .setInterestRateBps(1800)
            .setTenorMonths(6)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanListed> record = new ConsumerRecord<>("loan.listed", 0, 0L, "key", payload);

    consumer.onLoanListed(record, "event-2".getBytes());

    verify(listingRepository, never()).save(any());
    verify(processedEventRepository, never()).save(any());
  }
}

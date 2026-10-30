package com.lendbridge.marketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.loan.avro.LoanDelisted;
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
class LoanDelistedEventConsumerTest {

  @Mock private ListingRepository listingRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LoanDelistedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanDelistedEventConsumer(listingRepository, processedEventRepository);
  }

  @Test
  void onLoanDelisted_existingListing_marksItDelisted() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    Instant now = Instant.now();
    Listing listing =
        Listing.builder()
            .id(loanId)
            .borrowerUserId(UUID.randomUUID())
            .amountKobo(300_000L)
            .interestRateBps(1800)
            .tenorMonths(6)
            .purpose("BUSINESS")
            .status(ListingStatus.ACTIVE)
            .listedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    when(listingRepository.findById(loanId)).thenReturn(Optional.of(listing));
    LoanDelisted payload = LoanDelisted.newBuilder().setLoanId(loanId.toString()).setOccurredAtEpochMillis(now.toEpochMilli()).build();
    ConsumerRecord<String, LoanDelisted> record = new ConsumerRecord<>("loan.delisted", 0, 0L, loanId.toString(), payload);

    consumer.onLoanDelisted(record, "event-1".getBytes());

    ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
    verify(listingRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ListingStatus.DELISTED);
  }

  @Test
  void onLoanDelisted_noMatchingListing_isANoOpNotAnError() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    when(listingRepository.findById(loanId)).thenReturn(Optional.empty());
    LoanDelisted payload =
        LoanDelisted.newBuilder().setLoanId(loanId.toString()).setOccurredAtEpochMillis(System.currentTimeMillis()).build();
    ConsumerRecord<String, LoanDelisted> record = new ConsumerRecord<>("loan.delisted", 0, 0L, loanId.toString(), payload);

    consumer.onLoanDelisted(record, "event-2".getBytes());

    verify(listingRepository, never()).save(any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void onLoanDelisted_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);
    LoanDelisted payload =
        LoanDelisted.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanDelisted> record = new ConsumerRecord<>("loan.delisted", 0, 0L, "key", payload);

    consumer.onLoanDelisted(record, "event-3".getBytes());

    verify(listingRepository, never()).findById(any());
    verify(processedEventRepository, never()).save(any());
  }
}

package com.lendbridge.investment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.model.LoanListing;
import com.lendbridge.investment.repository.LoanListingRepository;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanDelisted;
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

  @Mock private LoanListingRepository loanListingRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LoanDelistedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanDelistedEventConsumer(loanListingRepository, processedEventRepository);
  }

  @Test
  void onLoanDelisted_existingListing_marksItInactive() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    LoanListing listing =
        LoanListing.builder().id(loanId).borrowerUserId(UUID.randomUUID()).amountKobo(1_000_000L).active(true).build();
    when(loanListingRepository.findById(loanId)).thenReturn(Optional.of(listing));
    LoanDelisted payload = LoanDelisted.newBuilder().setLoanId(loanId.toString()).setOccurredAtEpochMillis(1L).build();
    ConsumerRecord<String, LoanDelisted> record = new ConsumerRecord<>("loan.delisted", 0, 0L, loanId.toString(), payload);

    consumer.onLoanDelisted(record, "event-1".getBytes());

    ArgumentCaptor<LoanListing> captor = ArgumentCaptor.forClass(LoanListing.class);
    verify(loanListingRepository).save(captor.capture());
    assertThat(captor.getValue().isActive()).isFalse();
    verify(processedEventRepository).save(any());
  }

  @Test
  void onLoanDelisted_noMatchingListing_isANoOpNotAnError() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    when(loanListingRepository.findById(loanId)).thenReturn(Optional.empty());
    LoanDelisted payload = LoanDelisted.newBuilder().setLoanId(loanId.toString()).setOccurredAtEpochMillis(1L).build();
    ConsumerRecord<String, LoanDelisted> record = new ConsumerRecord<>("loan.delisted", 0, 0L, loanId.toString(), payload);

    consumer.onLoanDelisted(record, "event-2".getBytes());

    verify(loanListingRepository, never()).save(any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void onLoanDelisted_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);
    LoanDelisted payload =
        LoanDelisted.newBuilder().setLoanId(UUID.randomUUID().toString()).setOccurredAtEpochMillis(1L).build();
    ConsumerRecord<String, LoanDelisted> record = new ConsumerRecord<>("loan.delisted", 0, 0L, "key", payload);

    consumer.onLoanDelisted(record, "event-3".getBytes());

    verify(loanListingRepository, never()).findById(any());
    verify(processedEventRepository, never()).save(any());
  }
}

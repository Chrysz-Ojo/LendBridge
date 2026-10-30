package com.lendbridge.investment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.model.LoanListing;
import com.lendbridge.investment.repository.LoanListingRepository;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanListed;
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

  @Mock private LoanListingRepository loanListingRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  private LoanListedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanListedEventConsumer(loanListingRepository, processedEventRepository);
  }

  @Test
  void onLoanListed_newEvent_savesAnActiveLoanListing() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID loanId = UUID.randomUUID();
    UUID borrowerId = UUID.randomUUID();
    LoanListed payload =
        LoanListed.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(borrowerId.toString())
            .setAmountKobo(1_000_000L)
            .setInterestRateBps(1800)
            .setTenorMonths(6)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanListed> record = new ConsumerRecord<>("loan.listed", 0, 0L, loanId.toString(), payload);

    consumer.onLoanListed(record, "event-1".getBytes());

    ArgumentCaptor<LoanListing> captor = ArgumentCaptor.forClass(LoanListing.class);
    verify(loanListingRepository).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(loanId);
    assertThat(captor.getValue().getBorrowerUserId()).isEqualTo(borrowerId);
    assertThat(captor.getValue().getAmountKobo()).isEqualTo(1_000_000L);
    assertThat(captor.getValue().isActive()).isTrue();
    verify(processedEventRepository).save(any());
  }

  @Test
  void onLoanListed_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    LoanListed payload =
        LoanListed.newBuilder()
            .setLoanId(UUID.randomUUID().toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(1_000_000L)
            .setInterestRateBps(1800)
            .setTenorMonths(6)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, LoanListed> record = new ConsumerRecord<>("loan.listed", 0, 0L, "key", payload);

    consumer.onLoanListed(record, "event-2".getBytes());

    verify(loanListingRepository, never()).save(any());
    verify(processedEventRepository, never()).save(any());
  }
}

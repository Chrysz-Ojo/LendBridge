package com.lendbridge.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.repository.ProcessedEventRepository;
import com.lendbridge.notification.service.NotificationService;
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

  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private NotificationService notificationService;

  private LoanListedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanListedEventConsumer(processedEventRepository, notificationService);
  }

  @Test
  void onLoanListed_newEvent_dispatchesEmailToBorrower() {
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

    ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notificationService).dispatch(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(borrowerId);
    assertThat(captor.getValue().getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(captor.getValue().getType()).isEqualTo(NotificationType.LOAN_LISTED);
    assertThat(captor.getValue().getVariables()).containsEntry("amountKobo", "300000");
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

    verify(notificationService, never()).dispatch(any());
    verify(processedEventRepository, never()).save(any());
  }
}

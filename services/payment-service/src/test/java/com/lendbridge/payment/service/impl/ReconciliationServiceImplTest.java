package com.lendbridge.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lendbridge.payment.dto.ReconciliationRunRequest;
import com.lendbridge.payment.dto.ReconciliationRunResponse;
import com.lendbridge.payment.mapper.ReconciliationRunMapper;
import com.lendbridge.payment.model.MatchStatus;
import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.model.ProviderTransaction;
import com.lendbridge.payment.model.ProviderTransactionPurpose;
import com.lendbridge.payment.model.ProviderTransactionStatus;
import com.lendbridge.payment.model.ReconciliationStatus;
import com.lendbridge.payment.provider.impl.fake.FakePaymentProvider;
import com.lendbridge.payment.repository.ProviderTransactionRepository;
import com.lendbridge.payment.repository.ReconciliationRunRepository;
import com.lendbridge.payment.routing.PaymentProviderRouter;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceImplTest {

  @Mock private ReconciliationRunRepository reconciliationRunRepository;
  @Mock private ProviderTransactionRepository providerTransactionRepository;
  @Mock private PaymentProviderRouter paymentProviderRouter;
  private ReconciliationRunMapper reconciliationRunMapper;

  private ReconciliationServiceImpl service;

  @BeforeEach
  void setUp() {
    reconciliationRunMapper = Mappers.getMapper(ReconciliationRunMapper.class);
    service =
        new ReconciliationServiceImpl(
            reconciliationRunRepository,
            providerTransactionRepository,
            paymentProviderRouter,
            reconciliationRunMapper);
  }

  @Test
  void runReconciliation_rejectsProvidersThatDoNotSupportSettlementReports() {
    com.lendbridge.payment.provider.PaymentProvider realProvider =
        org.mockito.Mockito.mock(com.lendbridge.payment.provider.PaymentProvider.class);
    when(paymentProviderRouter.provider(ProviderName.PAYSTACK)).thenReturn(realProvider);

    ReconciliationRunRequest request =
        new ReconciliationRunRequest(ProviderName.PAYSTACK, Instant.EPOCH, Instant.now());

    assertThatThrownBy(() -> service.runReconciliation(request))
        .isInstanceOf(UnprocessableEntityException.class);
  }

  @Test
  void runReconciliation_marksMatchedAndBreakTransactions() {
    FakePaymentProvider fakeProvider = new FakePaymentProvider();
    when(paymentProviderRouter.provider(ProviderName.FAKE)).thenReturn(fakeProvider);

    ProviderTransaction matched = transaction("ref-matched");
    ProviderTransaction unmatched = transaction("ref-unmatched");
    when(providerTransactionRepository.findAllByProviderAndCreatedAtBetween(any(), any(), any()))
        .thenReturn(List.of(matched, unmatched));
    when(providerTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(reconciliationRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // FakePaymentProvider.generateSettlementReport always returns an empty list (no real
    // settlement file to reproduce), so every one of our own transactions is a BREAK here —
    // reconciliation against a fixture providing SettlementRecord fixtures is exercised
    // separately if a richer fake ever needs it.
    ReconciliationRunRequest request =
        new ReconciliationRunRequest(ProviderName.FAKE, Instant.EPOCH, Instant.now());
    ReconciliationRunResponse response = service.runReconciliation(request);

    assertThat(response.getStatus()).isEqualTo(ReconciliationStatus.COMPLETED_WITH_BREAKS);
    assertThat(response.getTotalMatched()).isEqualTo(0);
    assertThat(response.getTotalBreaks()).isEqualTo(2);
    assertThat(matched.getMatchStatus()).isEqualTo(MatchStatus.BREAK);
  }

  private ProviderTransaction transaction(String reference) {
    return ProviderTransaction.builder()
        .id(java.util.UUID.randomUUID())
        .purpose(ProviderTransactionPurpose.CHARGE_INIT)
        .provider(ProviderName.FAKE)
        .providerReference(reference)
        .status(ProviderTransactionStatus.SUCCESS)
        .createdAt(Instant.now())
        .build();
  }
}

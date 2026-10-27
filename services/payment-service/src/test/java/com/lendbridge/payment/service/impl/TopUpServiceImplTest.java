package com.lendbridge.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lendbridge.payment.dto.TopUpRequest;
import com.lendbridge.payment.dto.TopUpResponse;
import com.lendbridge.payment.mapper.TopUpMapper;
import com.lendbridge.payment.model.PaymentIntent;
import com.lendbridge.payment.model.PaymentIntentStatus;
import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.provider.ProviderChargeResult;
import com.lendbridge.payment.provider.ProviderOperationStatus;
import com.lendbridge.payment.repository.PaymentIntentRepository;
import com.lendbridge.payment.repository.ProviderTransactionRepository;
import com.lendbridge.payment.routing.PaymentProviderRouter;
import com.lendbridge.payment.routing.RoutedChargeResult;
import com.lendbridge.payment.wallet.WalletGateway;
import com.lendbridge.payment.wallet.WalletSummaryResponse;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopUpServiceImplTest {

  @Mock private PaymentIntentRepository paymentIntentRepository;
  @Mock private ProviderTransactionRepository providerTransactionRepository;
  @Mock private WalletGateway walletGateway;
  @Mock private PaymentProviderRouter paymentProviderRouter;
  private TopUpMapper topUpMapper;

  private TopUpServiceImpl service;
  private UUID userId;
  private WalletSummaryResponse wallet;

  @BeforeEach
  void setUp() {
    topUpMapper = Mappers.getMapper(TopUpMapper.class);
    service =
        new TopUpServiceImpl(
            paymentIntentRepository,
            providerTransactionRepository,
            walletGateway,
            paymentProviderRouter,
            topUpMapper,
            new IdempotencyOperations());
    userId = UUID.randomUUID();
    wallet =
        new WalletSummaryResponse(
            UUID.randomUUID(), UUID.randomUUID(), "NGN", 100_000L, 0L, 100_000L, false);
  }

  @Test
  void createTopUp_persistsAwaitingProviderStatus_whenChargeIsAcceptedByProvider() {
    when(paymentIntentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    when(walletGateway.getWalletSummary()).thenReturn(wallet);
    when(paymentProviderRouter.initiateTopUpCharge(any()))
        .thenReturn(
            new RoutedChargeResult(
                ProviderName.FAKE,
                ProviderChargeResult.builder()
                    .providerReference("fake-ref-1")
                    .status(ProviderOperationStatus.SUCCESSFUL)
                    .rawResponseJson("{}")
                    .build()));
    when(paymentIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TopUpRequest request = new TopUpRequest(5_000L, "NGN", "user@example.com");
    TopUpResponse response = service.createTopUp(userId, request, "key-1");

    assertThat(response.getStatus()).isEqualTo(PaymentIntentStatus.AWAITING_PROVIDER);
    assertThat(response.getProvider()).isEqualTo(ProviderName.FAKE);
  }

  @Test
  void createTopUp_persistsFailedStatus_whenProviderRejectsTheCharge() {
    when(paymentIntentRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
    when(walletGateway.getWalletSummary()).thenReturn(wallet);
    when(paymentProviderRouter.initiateTopUpCharge(any()))
        .thenReturn(
            new RoutedChargeResult(
                ProviderName.FAKE,
                ProviderChargeResult.builder()
                    .providerReference("fake-ref-2")
                    .status(ProviderOperationStatus.FAILED)
                    .rawResponseJson("{}")
                    .build()));
    when(paymentIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TopUpRequest request = new TopUpRequest(5_000L, "NGN", "user@example.com");
    TopUpResponse response = service.createTopUp(userId, request, "key-2");

    assertThat(response.getStatus()).isEqualTo(PaymentIntentStatus.FAILED);
  }

  @Test
  void createTopUp_idempotentReplay_doesNotCallTheProviderAgain() {
    PaymentIntent existing =
        PaymentIntent.builder()
            .id(UUID.randomUUID())
            .status(PaymentIntentStatus.AWAITING_PROVIDER)
            .provider(ProviderName.FAKE)
            .amountKobo(5_000L)
            .currency("NGN")
            .idempotencyKey("key-3")
            .build();
    when(paymentIntentRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.of(existing));

    TopUpRequest request = new TopUpRequest(5_000L, "NGN", "user@example.com");
    TopUpResponse response = service.createTopUp(userId, request, "key-3");

    assertThat(response.getId()).isEqualTo(existing.getId());
    org.mockito.Mockito.verifyNoInteractions(paymentProviderRouter, walletGateway);
  }
}

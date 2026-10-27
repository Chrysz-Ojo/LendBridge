package com.lendbridge.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.payment.authentication.AuthGateway;
import com.lendbridge.payment.dto.WithdrawalRequest;
import com.lendbridge.payment.dto.WithdrawalResponse;
import com.lendbridge.payment.mapper.WithdrawalMapper;
import com.lendbridge.payment.model.PayoutStatus;
import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.provider.ProviderOperationStatus;
import com.lendbridge.payment.provider.ProviderPayoutResult;
import com.lendbridge.payment.repository.PayoutRepository;
import com.lendbridge.payment.repository.ProviderTransactionRepository;
import com.lendbridge.payment.routing.PaymentProviderRouter;
import com.lendbridge.payment.wallet.BankAccountPayoutDetails;
import com.lendbridge.payment.wallet.WalletGateway;
import com.lendbridge.payment.wallet.WalletSummaryResponse;
import com.lendbridge.starter.exception.UnauthorizedException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

  @Mock private PayoutRepository payoutRepository;
  @Mock private ProviderTransactionRepository providerTransactionRepository;
  @Mock private WalletGateway walletGateway;
  @Mock private AuthGateway authGateway;
  @Mock private PaymentProviderRouter paymentProviderRouter;
  private WithdrawalMapper withdrawalMapper;

  private WithdrawalServiceImpl service;
  private UUID userId;
  private UUID bankAccountId;
  private WalletSummaryResponse wallet;
  private BankAccountPayoutDetails bankAccount;

  @BeforeEach
  void setUp() throws Exception {
    withdrawalMapper = Mappers.getMapper(WithdrawalMapper.class);
    service =
        new WithdrawalServiceImpl(
            payoutRepository,
            providerTransactionRepository,
            walletGateway,
            authGateway,
            paymentProviderRouter,
            withdrawalMapper,
            new IdempotencyOperations());
    setDailyLimit(service, 1_000_000L);

    userId = UUID.randomUUID();
    bankAccountId = UUID.randomUUID();
    wallet =
        new WalletSummaryResponse(
            UUID.randomUUID(), UUID.randomUUID(), "NGN", 100_000L, 0L, 100_000L, false);
    bankAccount =
        new BankAccountPayoutDetails(bankAccountId, "058", "GTBank", "0123456789", "Jane Doe", "VERIFIED");
  }

  private static void setDailyLimit(WithdrawalServiceImpl service, long value) throws Exception {
    Field field = WithdrawalServiceImpl.class.getDeclaredField("dailyLimitKobo");
    field.setAccessible(true);
    field.set(service, value);
  }

  @Test
  void createWithdrawal_rejectsWhenBankAccountNotVerified() {
    when(payoutRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    when(walletGateway.getWalletSummary()).thenReturn(wallet);
    when(walletGateway.getBankAccountForPayout(bankAccountId))
        .thenReturn(
            new BankAccountPayoutDetails(bankAccountId, "058", "GTBank", "0123456789", "Jane Doe", "PENDING"));

    WithdrawalRequest request = new WithdrawalRequest(5_000L, bankAccountId, "123456");

    assertThatThrownBy(() -> service.createWithdrawal(userId, request, "key-1"))
        .isInstanceOf(UnprocessableEntityException.class);
    verify(payoutRepository, never()).save(any());
  }

  @Test
  void createWithdrawal_rejectsWhenAmountExceedsAvailableBalance() {
    when(payoutRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
    when(walletGateway.getWalletSummary()).thenReturn(wallet);
    when(walletGateway.getBankAccountForPayout(bankAccountId)).thenReturn(bankAccount);

    WithdrawalRequest request = new WithdrawalRequest(200_000L, bankAccountId, "123456");

    assertThatThrownBy(() -> service.createWithdrawal(userId, request, "key-2"))
        .isInstanceOf(UnprocessableEntityException.class);
    verify(payoutRepository, never()).save(any());
  }

  @Test
  void createWithdrawal_rejectsWhenStepUpVerificationFails() {
    when(payoutRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
    org.mockito.Mockito.doThrow(new UnauthorizedException("TOTP_CODE_INVALID", "bad code"))
        .when(authGateway)
        .verifyStepUp("wrong-code");

    WithdrawalRequest request = new WithdrawalRequest(5_000L, bankAccountId, "wrong-code");

    assertThatThrownBy(() -> service.createWithdrawal(userId, request, "key-3"))
        .isInstanceOf(UnauthorizedException.class);
    verify(walletGateway, never()).getWalletSummary();
  }

  @Test
  void createWithdrawal_succeedsAndMarksProcessing_whenProviderAcceptsTheTransfer() {
    when(payoutRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());
    when(walletGateway.getWalletSummary()).thenReturn(wallet);
    when(walletGateway.getBankAccountForPayout(bankAccountId)).thenReturn(bankAccount);
    when(paymentProviderRouter.primaryProviderName()).thenReturn(ProviderName.FAKE);
    when(paymentProviderRouter.initiatePayout(any()))
        .thenReturn(
            ProviderPayoutResult.builder()
                .providerReference("fake-payout-1")
                .status(ProviderOperationStatus.SUCCESSFUL)
                .rawResponseJson("{}")
                .build());
    when(payoutRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WithdrawalRequest request = new WithdrawalRequest(5_000L, bankAccountId, "123456");
    WithdrawalResponse response = service.createWithdrawal(userId, request, "key-4");

    assertThat(response.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
    verify(authGateway).verifyStepUp("123456");
  }

  @Test
  void createWithdrawal_exceedingDailyLimit_isRejected() throws Exception {
    setDailyLimit(service, 10_000L);
    when(payoutRepository.findByIdempotencyKey("key-5")).thenReturn(Optional.empty());
    when(walletGateway.getWalletSummary()).thenReturn(wallet);
    when(walletGateway.getBankAccountForPayout(bankAccountId)).thenReturn(bankAccount);
    when(payoutRepository.sumWithdrawalsByUserIdSince(any(), any())).thenReturn(8_000L);

    WithdrawalRequest request = new WithdrawalRequest(5_000L, bankAccountId, "123456");

    assertThatThrownBy(() -> service.createWithdrawal(userId, request, "key-5"))
        .isInstanceOf(UnprocessableEntityException.class)
        .hasMessageContaining("daily");
    verify(payoutRepository, never()).save(any());
  }
}

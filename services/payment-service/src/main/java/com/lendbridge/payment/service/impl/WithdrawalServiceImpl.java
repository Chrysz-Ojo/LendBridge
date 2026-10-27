package com.lendbridge.payment.service.impl;

import com.lendbridge.payment.authentication.AuthGateway;
import com.lendbridge.payment.dto.WithdrawalRequest;
import com.lendbridge.payment.dto.WithdrawalResponse;
import com.lendbridge.payment.mapper.WithdrawalMapper;
import com.lendbridge.payment.model.PayoutStatus;
import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.model.ProviderTransaction;
import com.lendbridge.payment.model.ProviderTransactionPurpose;
import com.lendbridge.payment.model.ProviderTransactionStatus;
import com.lendbridge.payment.model.Payout;
import com.lendbridge.payment.provider.PayoutRequest;
import com.lendbridge.payment.provider.ProviderOperationStatus;
import com.lendbridge.payment.provider.ProviderPayoutResult;
import com.lendbridge.payment.repository.PayoutRepository;
import com.lendbridge.payment.repository.ProviderTransactionRepository;
import com.lendbridge.payment.routing.PaymentProviderRouter;
import com.lendbridge.payment.service.WithdrawalService;
import com.lendbridge.payment.wallet.BankAccountPayoutDetails;
import com.lendbridge.payment.wallet.WalletGateway;
import com.lendbridge.payment.wallet.WalletSummaryResponse;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * v1 does a check-then-act balance read via wallet-service rather than placing a hold between
 * withdrawal-request and provider-settlement — an accepted, narrow race (see the approved plan's
 * "explicitly deferred" list), not a hold-based reservation like wallet-service's own holds.
 */
@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

  private final PayoutRepository payoutRepository;
  private final ProviderTransactionRepository providerTransactionRepository;
  private final WalletGateway walletGateway;
  private final AuthGateway authGateway;
  private final PaymentProviderRouter paymentProviderRouter;
  private final WithdrawalMapper withdrawalMapper;
  private final IdempotencyOperations idempotencyOperations;

  @Value("${payment.withdrawal.daily-limit-kobo}")
  private long dailyLimitKobo;

  @Override
  @Transactional
  public WithdrawalResponse createWithdrawal(
      UUID userId, WithdrawalRequest request, String idempotencyKey) {
    return idempotencyOperations.runIdempotently(
        () -> payoutRepository.findByIdempotencyKey(idempotencyKey).map(withdrawalMapper::toResponse),
        () -> createWithdrawalRecord(userId, request, idempotencyKey));
  }

  private WithdrawalResponse createWithdrawalRecord(
      UUID userId, WithdrawalRequest request, String idempotencyKey) {
    // Fail-closed 2FA: any failure to verify (wrong code, or authentication-service unreachable)
    // aborts the withdrawal before anything is persisted or any money moves.
    authGateway.verifyStepUp(request.getTotpCode());

    WalletSummaryResponse wallet = walletGateway.getWalletSummary();
    BankAccountPayoutDetails bankAccount =
        walletGateway.getBankAccountForPayout(request.getBankAccountId());

    if (!"VERIFIED".equals(bankAccount.getVerificationStatus())) {
      throw new UnprocessableEntityException(
          "BANK_ACCOUNT_NOT_VERIFIED", "Bank account must be verified before a payout");
    }
    if (request.getAmountKobo() > wallet.getAvailableKobo()) {
      throw new UnprocessableEntityException(
          "INSUFFICIENT_FUNDS", "Withdrawal amount exceeds available wallet balance");
    }

    Instant startOfDay = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    long alreadyWithdrawnToday = payoutRepository.sumWithdrawalsByUserIdSince(userId, startOfDay);
    if (alreadyWithdrawnToday + request.getAmountKobo() > dailyLimitKobo) {
      throw new UnprocessableEntityException(
          "WITHDRAWAL_DAILY_LIMIT_EXCEEDED", "Withdrawal would exceed the daily withdrawal limit");
    }

    UUID id = UuidV7.generate();
    ProviderName provider = paymentProviderRouter.primaryProviderName();
    ProviderPayoutResult result =
        paymentProviderRouter.initiatePayout(
            new PayoutRequest(
                id.toString(),
                request.getAmountKobo(),
                wallet.getCurrency(),
                bankAccount.getBankCode(),
                bankAccount.getAccountNumber(),
                bankAccount.getAccountHolderName(),
                idempotencyKey));

    // Final SUCCESSFUL/FAILED status is only ever set by the webhook handler, which also
    // publishes payment.succeeded/payment.failed — a synchronous PENDING/SUCCESSFUL result here
    // just means the provider accepted the transfer request, not that it settled.
    PayoutStatus status =
        result.getStatus() == ProviderOperationStatus.FAILED
            ? PayoutStatus.FAILED
            : PayoutStatus.PROCESSING;

    Instant now = Instant.now();
    Payout payout =
        Payout.builder()
            .id(id)
            .userId(userId)
            .walletId(wallet.getId())
            .walletLedgerAccountId(wallet.getLedgerAccountId())
            .bankAccountId(request.getBankAccountId())
            .amountKobo(request.getAmountKobo())
            .currency(wallet.getCurrency())
            .provider(provider)
            .providerReference(result.getProviderReference())
            .status(status)
            .idempotencyKey(idempotencyKey)
            .createdAt(now)
            .updatedAt(now)
            .build();
    payoutRepository.save(payout);

    providerTransactionRepository.save(
        ProviderTransaction.builder()
            .id(UuidV7.generate())
            .payoutId(id)
            .purpose(ProviderTransactionPurpose.TRANSFER_INITIATE)
            .provider(provider)
            .providerReference(result.getProviderReference())
            .rawResponseJson(result.getRawResponseJson())
            .status(
                result.getStatus() == ProviderOperationStatus.FAILED
                    ? ProviderTransactionStatus.FAILED
                    : ProviderTransactionStatus.SUCCESS)
            .createdAt(now)
            .build());

    return withdrawalMapper.toResponse(payout);
  }

  @Override
  @Transactional(readOnly = true)
  public WithdrawalResponse getWithdrawal(UUID userId, UUID id) {
    Payout payout =
        payoutRepository
            .findById(id)
            .filter(p -> p.getUserId().equals(userId))
            .orElseThrow(() -> new NotFoundException("WITHDRAWAL_NOT_FOUND", "Withdrawal not found"));
    return withdrawalMapper.toResponse(payout);
  }
}

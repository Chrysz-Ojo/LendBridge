package com.lendbridge.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.payment.dto.TopUpRequest;
import com.lendbridge.payment.dto.TopUpResponse;
import com.lendbridge.payment.dto.VirtualAccountTopUpRequest;
import com.lendbridge.payment.dto.VirtualAccountTopUpResponse;
import com.lendbridge.payment.mapper.TopUpMapper;
import com.lendbridge.payment.model.PaymentIntent;
import com.lendbridge.payment.model.PaymentIntentStatus;
import com.lendbridge.payment.model.PaymentPurpose;
import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.model.ProviderTransaction;
import com.lendbridge.payment.model.ProviderTransactionPurpose;
import com.lendbridge.payment.model.ProviderTransactionStatus;
import com.lendbridge.payment.provider.ChargeRequest;
import com.lendbridge.payment.provider.ProviderOperationStatus;
import com.lendbridge.payment.provider.ProviderVirtualAccountResult;
import com.lendbridge.payment.provider.VirtualAccountRequest;
import com.lendbridge.payment.repository.PaymentIntentRepository;
import com.lendbridge.payment.repository.ProviderTransactionRepository;
import com.lendbridge.payment.routing.PaymentProviderRouter;
import com.lendbridge.payment.routing.RoutedChargeResult;
import com.lendbridge.payment.service.TopUpService;
import com.lendbridge.payment.wallet.RegisterVirtualAccountCommand;
import com.lendbridge.payment.wallet.WalletGateway;
import com.lendbridge.payment.wallet.WalletSummaryResponse;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TopUpServiceImpl implements TopUpService {

  private final PaymentIntentRepository paymentIntentRepository;
  private final ProviderTransactionRepository providerTransactionRepository;
  private final WalletGateway walletGateway;
  private final PaymentProviderRouter paymentProviderRouter;
  private final TopUpMapper topUpMapper;
  private final IdempotencyOperations idempotencyOperations;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  @Transactional
  public TopUpResponse createTopUp(UUID userId, TopUpRequest request, String idempotencyKey) {
    return idempotencyOperations.runIdempotently(
        () -> paymentIntentRepository.findByIdempotencyKey(idempotencyKey).map(topUpMapper::toResponse),
        () -> createTopUpRecord(userId, request, idempotencyKey));
  }

  private TopUpResponse createTopUpRecord(UUID userId, TopUpRequest request, String idempotencyKey) {
    WalletSummaryResponse wallet = walletGateway.getWalletSummary();
    UUID id = UuidV7.generate();

    RoutedChargeResult routed =
        paymentProviderRouter.initiateTopUpCharge(
            new ChargeRequest(
                id.toString(),
                request.getAmountKobo(),
                request.getCurrency(),
                request.getCustomerEmail(),
                idempotencyKey));

    // Final SUCCEEDED status is only ever set by the webhook handler, which also posts
    // payment.succeeded — a synchronous SUCCESSFUL result from initiateCharge here just means the
    // provider accepted the charge, not that money has actually moved yet.
    PaymentIntentStatus status =
        routed.getResult().getStatus() == ProviderOperationStatus.FAILED
            ? PaymentIntentStatus.FAILED
            : PaymentIntentStatus.AWAITING_PROVIDER;

    Instant now = Instant.now();
    PaymentIntent paymentIntent =
        PaymentIntent.builder()
            .id(id)
            .walletId(wallet.getId())
            .walletLedgerAccountId(wallet.getLedgerAccountId())
            .userId(userId)
            .purpose(PaymentPurpose.TOP_UP)
            .amountKobo(request.getAmountKobo())
            .currency(request.getCurrency())
            .status(status)
            .provider(routed.getProvider())
            .providerReference(routed.getResult().getProviderReference())
            .checkoutUrl(routed.getResult().getCheckoutUrl())
            .idempotencyKey(idempotencyKey)
            .createdAt(now)
            .updatedAt(now)
            .build();
    paymentIntentRepository.save(paymentIntent);

    providerTransactionRepository.save(
        ProviderTransaction.builder()
            .id(UuidV7.generate())
            .paymentIntentId(id)
            .purpose(ProviderTransactionPurpose.CHARGE_INIT)
            .provider(routed.getProvider())
            .providerReference(routed.getResult().getProviderReference())
            .rawResponseJson(routed.getResult().getRawResponseJson())
            .status(
                routed.getResult().getStatus() == ProviderOperationStatus.FAILED
                    ? ProviderTransactionStatus.FAILED
                    : ProviderTransactionStatus.SUCCESS)
            .createdAt(now)
            .build());

    return topUpMapper.toResponse(paymentIntent);
  }

  @Override
  @Transactional(readOnly = true)
  public TopUpResponse getTopUp(UUID userId, UUID id) {
    PaymentIntent paymentIntent =
        paymentIntentRepository
            .findById(id)
            .filter(pi -> pi.getUserId().equals(userId))
            .orElseThrow(() -> new NotFoundException("TOP_UP_NOT_FOUND", "Top-up not found"));
    return topUpMapper.toResponse(paymentIntent);
  }

  @Override
  @Transactional
  public VirtualAccountTopUpResponse createVirtualAccount(
      UUID userId, VirtualAccountTopUpRequest request, String idempotencyKey) {
    return idempotencyOperations.runIdempotently(
        () ->
            providerTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(this::toVirtualAccountResponse),
        () -> createVirtualAccountRecord(userId, request, idempotencyKey));
  }

  private VirtualAccountTopUpResponse createVirtualAccountRecord(
      UUID userId, VirtualAccountTopUpRequest request, String idempotencyKey) {
    WalletSummaryResponse wallet = walletGateway.getWalletSummary();

    ProviderVirtualAccountResult result =
        paymentProviderRouter.provisionVirtualAccount(
            new VirtualAccountRequest(
                wallet.getId().toString(),
                request.getCustomerEmail(),
                request.getCustomerName(),
                request.getBvnOrNin()));

    ProviderName provider = paymentProviderRouter.primaryProviderName();
    VirtualAccountTopUpResponse response =
        VirtualAccountTopUpResponse.builder()
            .accountNumber(result.getAccountNumber())
            .bankName(result.getBankName())
            .provider(provider)
            .providerReference(result.getProviderReference())
            .build();

    walletGateway.registerVirtualAccount(
        new RegisterVirtualAccountCommand(
            result.getBankName(), result.getAccountNumber(), result.getProviderReference()));

    Instant now = Instant.now();
    String responseJson = writeResponseJson(response);
    providerTransactionRepository.save(
        ProviderTransaction.builder()
            .id(UuidV7.generate())
            .purpose(ProviderTransactionPurpose.VIRTUAL_ACCOUNT_CREATE)
            .provider(provider)
            .providerReference(result.getProviderReference())
            .idempotencyKey(idempotencyKey)
            .rawResponseJson(responseJson)
            .status(ProviderTransactionStatus.SUCCESS)
            .createdAt(now)
            .build());

    return response;
  }

  private VirtualAccountTopUpResponse toVirtualAccountResponse(ProviderTransaction transaction) {
    try {
      return objectMapper.readValue(
          transaction.getRawResponseJson(), VirtualAccountTopUpResponse.class);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Unable to deserialize stored virtual account response for idempotency replay", e);
    }
  }

  private String writeResponseJson(VirtualAccountTopUpResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize virtual account response", e);
    }
  }
}

package com.lendbridge.payment.service.impl;

import com.lendbridge.payment.dto.ReconciliationRunRequest;
import com.lendbridge.payment.dto.ReconciliationRunResponse;
import com.lendbridge.payment.mapper.ReconciliationRunMapper;
import com.lendbridge.payment.model.MatchStatus;
import com.lendbridge.payment.model.ProviderTransaction;
import com.lendbridge.payment.model.ReconciliationRun;
import com.lendbridge.payment.model.ReconciliationStatus;
import com.lendbridge.payment.provider.PaymentProvider;
import com.lendbridge.payment.provider.SettlementRecord;
import com.lendbridge.payment.provider.SettlementReportSource;
import com.lendbridge.payment.repository.ProviderTransactionRepository;
import com.lendbridge.payment.repository.ReconciliationRunRepository;
import com.lendbridge.payment.routing.PaymentProviderRouter;
import com.lendbridge.payment.service.ReconciliationService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * On-demand only (ADR 0012, mirrors ledger-service's own ADR 0005) — nothing schedules this
 * automatically, and there's no real settlement file to test against yet, so matching is scoped
 * to provider reference presence rather than amount-level diffing.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

  private final ReconciliationRunRepository reconciliationRunRepository;
  private final ProviderTransactionRepository providerTransactionRepository;
  private final PaymentProviderRouter paymentProviderRouter;
  private final ReconciliationRunMapper reconciliationRunMapper;

  @Override
  @Transactional
  public ReconciliationRunResponse runReconciliation(ReconciliationRunRequest request) {
    PaymentProvider provider = paymentProviderRouter.provider(request.getProvider());
    if (!(provider instanceof SettlementReportSource settlementReportSource)) {
      throw new UnprocessableEntityException(
          "SETTLEMENT_REPORT_UNAVAILABLE",
          "Provider " + request.getProvider() + " does not support settlement reconciliation");
    }

    Instant startedAt = Instant.now();
    List<SettlementRecord> settlementRecords = settlementReportSource.generateSettlementReport(
            request.getProvider(), request.getPeriodStart(), request.getPeriodEnd());
    Map<String, SettlementRecord> settlementByReference = settlementRecords.stream()
            .collect(Collectors.toMap(SettlementRecord::getProviderReference, Function.identity(), (a, b) -> a));

    List<ProviderTransaction> ourTransactions = providerTransactionRepository.findAllByProviderAndCreatedAtBetween(
            request.getProvider(), request.getPeriodStart(), request.getPeriodEnd());

    UUID runId = UuidV7.generate();
    int matched = 0;
    for (ProviderTransaction transaction : ourTransactions) {
      MatchStatus matchStatus =
          settlementByReference.containsKey(transaction.getProviderReference())
              ? MatchStatus.MATCHED
              : MatchStatus.BREAK;
      if (matchStatus == MatchStatus.MATCHED) {
        matched++;
      }
      transaction.setReconciliationRunId(runId);
      transaction.setMatchStatus(matchStatus);
      providerTransactionRepository.save(transaction);
    }

    int totalBreaks = (ourTransactions.size() - matched) + (settlementRecords.size() - matched);
    Instant completedAt = Instant.now();

    ReconciliationRun run = ReconciliationRun.builder()
            .id(runId)
            .provider(request.getProvider())
            .periodStart(request.getPeriodStart())
            .periodEnd(request.getPeriodEnd())
            .status(totalBreaks == 0 ? ReconciliationStatus.COMPLETED : ReconciliationStatus.COMPLETED_WITH_BREAKS)
            .totalProviderRecords(settlementRecords.size())
            .totalMatched(matched)
            .totalBreaks(totalBreaks)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();
    reconciliationRunRepository.save(run);

    return reconciliationRunMapper.toResponse(run);
  }

  @Override
  @Transactional(readOnly = true)
  public ReconciliationRunResponse getReconciliationRun(UUID id) {
    ReconciliationRun run =
        reconciliationRunRepository
            .findById(id)
            .orElseThrow(
                () -> new NotFoundException("RECONCILIATION_RUN_NOT_FOUND", "Reconciliation run not found"));
    return reconciliationRunMapper.toResponse(run);
  }
}

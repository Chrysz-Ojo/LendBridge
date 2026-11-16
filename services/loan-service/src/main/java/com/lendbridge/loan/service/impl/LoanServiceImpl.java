package com.lendbridge.loan.service.impl;

import com.lendbridge.loan.document.AgreementPdfRenderer;
import com.lendbridge.loan.document.DocumentUploadClient;
import com.lendbridge.loan.document.DocumentUploadResponse;
import com.lendbridge.loan.dto.AgreementResponse;
import com.lendbridge.loan.dto.CreateLoanRequest;
import com.lendbridge.loan.dto.InstalmentResponse;
import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.RepaymentRequest;
import com.lendbridge.loan.dto.ScheduleResponse;
import com.lendbridge.loan.kyc.KycGateway;
import com.lendbridge.loan.kyc.KycStatusResponse;
import com.lendbridge.loan.mapper.LoanMapper;
import com.lendbridge.loan.model.Instalment;
import com.lendbridge.loan.model.InstalmentStatus;
import com.lendbridge.loan.model.LoanApplication;
import com.lendbridge.loan.model.LoanStatus;
import com.lendbridge.loan.model.RepaymentMethod;
import com.lendbridge.loan.model.RepaymentSchedule;
import com.lendbridge.loan.outbox.KafkaTopics;
import com.lendbridge.loan.outbox.LoanAgreementSignedPayload;
import com.lendbridge.loan.outbox.LoanDelistedPayload;
import com.lendbridge.loan.outbox.LoanDisbursedPayload;
import com.lendbridge.loan.outbox.LoanRepaidPayload;
import com.lendbridge.loan.outbox.LoanRepaymentRequestedPayload;
import com.lendbridge.loan.outbox.LoanSubmittedPayload;
import com.lendbridge.loan.repository.InstalmentRepository;
import com.lendbridge.loan.repository.LoanApplicationRepository;
import com.lendbridge.loan.repository.RepaymentScheduleRepository;
import com.lendbridge.loan.service.LoanService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1 only: the funding saga, agreement e-signature and repayment processing are deliberately
 * not here (see docs/adr/0015-loan-service-phase-1-scope.md). Trust-tier amount limits and
 * tenor/rate bounds are env-configurable (CLAUDE.md's "configurable limits" rule), mixed into the
 * constructor via {@code @Value} — same pattern as kyc-service's {@code VerificationProviderRouter}.
 */
@Service
public class LoanServiceImpl implements LoanService {

  private static final Logger log = LoggerFactory.getLogger(LoanServiceImpl.class);

  private final LoanApplicationRepository loanApplicationRepository;
  private final RepaymentScheduleRepository repaymentScheduleRepository;
  private final InstalmentRepository instalmentRepository;
  private final KycGateway kycGateway;
  private final OutboxEventWriter outboxEventWriter;
  private final AgreementPdfRenderer agreementPdfRenderer;
  private final DocumentUploadClient documentUploadClient;
  private final LoanMapper loanMapper;
  private final int tenorMinMonths;
  private final int tenorMaxMonths;
  private final int rateFloorBps;
  private final int rateCapBps;
  private final long tier1LimitKobo;
  private final long tier2LimitKobo;
  private final long tier3LimitKobo;
  private final int originationFeeBps;

  public LoanServiceImpl(
      LoanApplicationRepository loanApplicationRepository,
      RepaymentScheduleRepository repaymentScheduleRepository,
      InstalmentRepository instalmentRepository,
      KycGateway kycGateway,
      OutboxEventWriter outboxEventWriter,
      AgreementPdfRenderer agreementPdfRenderer,
      DocumentUploadClient documentUploadClient,
      LoanMapper loanMapper,
      @Value("${loan.tenor.min-months}") int tenorMinMonths,
      @Value("${loan.tenor.max-months}") int tenorMaxMonths,
      @Value("${loan.rate.floor-bps}") int rateFloorBps,
      @Value("${loan.rate.cap-bps}") int rateCapBps,
      @Value("${loan.trust-tier.tier1-limit-kobo}") long tier1LimitKobo,
      @Value("${loan.trust-tier.tier2-limit-kobo}") long tier2LimitKobo,
      @Value("${loan.trust-tier.tier3-limit-kobo}") long tier3LimitKobo,
      @Value("${loan.origination-fee-bps}") int originationFeeBps) {
    this.loanApplicationRepository = loanApplicationRepository;
    this.repaymentScheduleRepository = repaymentScheduleRepository;
    this.instalmentRepository = instalmentRepository;
    this.kycGateway = kycGateway;
    this.outboxEventWriter = outboxEventWriter;
    this.agreementPdfRenderer = agreementPdfRenderer;
    this.documentUploadClient = documentUploadClient;
    this.loanMapper = loanMapper;
    this.tenorMinMonths = tenorMinMonths;
    this.tenorMaxMonths = tenorMaxMonths;
    this.rateFloorBps = rateFloorBps;
    this.rateCapBps = rateCapBps;
    this.tier1LimitKobo = tier1LimitKobo;
    this.tier2LimitKobo = tier2LimitKobo;
    this.tier3LimitKobo = tier3LimitKobo;
    this.originationFeeBps = originationFeeBps;
  }

  @Override
  @Transactional
  public LoanResponse create(UUID borrowerUserId, CreateLoanRequest request) {
    if (request.getTenorMonths() < tenorMinMonths || request.getTenorMonths() > tenorMaxMonths) {
      throw new BadRequestException(
          "LOAN_TENOR_OUT_OF_RANGE",
          "Tenor must be between " + tenorMinMonths + " and " + tenorMaxMonths + " months");
    }
    if (request.getInterestRateBps() < rateFloorBps || request.getInterestRateBps() > rateCapBps) {
      throw new BadRequestException(
          "LOAN_RATE_OUT_OF_RANGE",
          "Interest rate must be between " + rateFloorBps + " and " + rateCapBps + " basis points");
    }
    enforceTrustTierLimit(request.getAmountKobo());

    Instant now = Instant.now();
    LoanApplication loan =
        LoanApplication.builder()
            .id(UuidV7.generate())
            .borrowerUserId(borrowerUserId)
            .amountKobo(request.getAmountKobo())
            .tenorMonths(request.getTenorMonths())
            .purpose(request.getPurpose())
            .purposeDetail(request.getPurposeDetail())
            .interestRateBps(request.getInterestRateBps())
            .status(LoanStatus.DRAFT)
            .reviewRequired(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    try {
      loanApplicationRepository.saveAndFlush(loan);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("LOAN_OPEN_REQUEST_EXISTS", "You already have an open loan request");
    }
    return toResponse(loan);
  }

  @Override
  @Transactional
  public LoanResponse submit(UUID borrowerUserId, UUID loanId) {
    LoanApplication loan = findOwned(borrowerUserId, loanId);
    if (loan.getStatus() != LoanStatus.DRAFT) {
      throw new ConflictException("LOAN_NOT_IN_DRAFT", "Only a Draft loan can be submitted for review");
    }
    Instant now = Instant.now();
    loan.setStatus(LoanStatus.UNDER_REVIEW);
    loan.setReviewRequired(true);
    loan.setUpdatedAt(now);

    outboxEventWriter.write(
        "LoanApplication",
        loan.getId().toString(),
        KafkaTopics.LOAN_SUBMITTED,
        LoanSubmittedPayload.builder()
            .loanId(loan.getId())
            .borrowerUserId(loan.getBorrowerUserId())
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    loanApplicationRepository.save(loan);
    return toResponse(loan);
  }

  @Override
  @Transactional
  public LoanResponse cancel(UUID borrowerUserId, UUID loanId) {
    LoanApplication loan = findOwned(borrowerUserId, loanId);
    if (loan.getStatus() != LoanStatus.DRAFT
        && loan.getStatus() != LoanStatus.UNDER_REVIEW
        && loan.getStatus() != LoanStatus.LISTED) {
      throw new ConflictException(
          "LOAN_NOT_CANCELLABLE", "A loan can only be cancelled while Draft, Under review or Listed");
    }
    boolean wasListed = loan.getStatus() == LoanStatus.LISTED;
    Instant now = Instant.now();
    loan.setStatus(LoanStatus.CANCELLED);
    loan.setReviewRequired(false);
    loan.setUpdatedAt(now);

    if (wasListed) {
      // marketplace-service's read model only has a listing to remove if this loan ever reached
      // Listed — a Draft/Under review cancellation never published loan.listed in the first place.
      outboxEventWriter.write(
          "LoanApplication",
          loan.getId().toString(),
          KafkaTopics.LOAN_DELISTED,
          LoanDelistedPayload.builder().loanId(loan.getId()).occurredAtEpochMillis(now.toEpochMilli()).build());
    }

    loanApplicationRepository.save(loan);
    return toResponse(loan);
  }

  @Override
  @Transactional(readOnly = true)
  public LoanResponse getOwn(UUID borrowerUserId, UUID loanId) {
    return toResponse(findOwned(borrowerUserId, loanId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<LoanResponse> listOwn(UUID borrowerUserId) {
    return loanApplicationRepository.findAllByBorrowerUserIdOrderByCreatedAtDesc(borrowerUserId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ScheduleResponse getSchedule(UUID borrowerUserId, UUID loanId) {
    return schedulePreviewFor(findOwned(borrowerUserId, loanId));
  }

  @Override
  @Transactional
  public AgreementResponse generateAgreement(UUID borrowerUserId, UUID loanId) {
    LoanApplication loan = findOwned(borrowerUserId, loanId);
    if (loan.getStatus() != LoanStatus.LISTED) {
      throw new ConflictException("LOAN_NOT_LISTED", "An agreement can only be generated once the loan is Listed");
    }
    if (loan.getAgreementDocumentId() != null) {
      return AgreementResponse.builder()
          .documentId(loan.getAgreementDocumentId())
          .templateVersion(agreementPdfRenderer.templateVersion())
          .generatedAt(loan.getUpdatedAt())
          .build();
    }

    byte[] pdfBytes = agreementPdfRenderer.render(loan);
    DocumentUploadResponse uploaded =
        documentUploadClient.uploadAgreement(pdfBytes, "loan-agreement-" + loan.getId() + ".pdf");

    Instant now = Instant.now();
    loan.setAgreementDocumentId(uploaded.getId());
    loan.setUpdatedAt(now);
    loanApplicationRepository.save(loan);

    return AgreementResponse.builder()
        .documentId(uploaded.getId())
        .templateVersion(agreementPdfRenderer.templateVersion())
        .generatedAt(now)
        .build();
  }

  @Override
  @Transactional
  public LoanResponse signAgreement(UUID borrowerUserId, UUID loanId) {
    LoanApplication loan = findOwned(borrowerUserId, loanId);
    if (loan.getStatus() != LoanStatus.FUNDED) {
      throw new ConflictException("LOAN_NOT_FUNDED", "An agreement can only be signed once the loan is fully funded");
    }

    Instant now = Instant.now();
    long originationFeeKobo =
        BigDecimal.valueOf(loan.getAmountKobo())
            .multiply(BigDecimal.valueOf(originationFeeBps))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_EVEN)
            .longValueExact();
    loan.setOriginationFeeKobo(originationFeeKobo);
    loan.setAgreementSignedAt(now);
    loan.setStatus(LoanStatus.DISBURSING);
    loan.setUpdatedAt(now);

    outboxEventWriter.write(
        "LoanApplication",
        loan.getId().toString(),
        KafkaTopics.LOAN_AGREEMENT_SIGNED,
        LoanAgreementSignedPayload.builder()
            .loanId(loan.getId())
            .borrowerUserId(loan.getBorrowerUserId())
            .amountKobo(loan.getAmountKobo())
            .originationFeeKobo(originationFeeKobo)
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    loanApplicationRepository.save(loan);
    return toResponse(loan);
  }

  @Override
  @Transactional
  public void recordCommitment(UUID loanId, long amountKobo) {
    Optional<LoanApplication> maybeLoan = loanApplicationRepository.findById(loanId);
    if (maybeLoan.isEmpty()) {
      log.warn("investment.committed for unknown loan {}", loanId);
      return;
    }
    LoanApplication loan = maybeLoan.get();
    if (loan.getStatus() != LoanStatus.LISTED && loan.getStatus() != LoanStatus.FUNDING) {
      log.warn("Ignoring investment.committed for loan {} in status {}", loanId, loan.getStatus());
      return;
    }

    loan.setFundedAmountKobo(loan.getFundedAmountKobo() + amountKobo);
    if (loan.getStatus() == LoanStatus.LISTED) {
      loan.setStatus(LoanStatus.FUNDING);
    }
    if (loan.getFundedAmountKobo() >= loan.getAmountKobo()) {
      loan.setStatus(LoanStatus.FUNDED);
    }
    loan.setUpdatedAt(Instant.now());
    loanApplicationRepository.save(loan);
  }

  @Override
  @Transactional
  public void recordCommitmentCancelled(UUID loanId, long amountKobo) {
    Optional<LoanApplication> maybeLoan = loanApplicationRepository.findById(loanId);
    if (maybeLoan.isEmpty()) {
      log.warn("investment.cancelled for unknown loan {}", loanId);
      return;
    }
    LoanApplication loan = maybeLoan.get();
    if (loan.getStatus() != LoanStatus.FUNDING && loan.getStatus() != LoanStatus.FUNDED) {
      log.warn("Ignoring investment.cancelled for loan {} in status {}", loanId, loan.getStatus());
      return;
    }

    loan.setFundedAmountKobo(Math.max(0, loan.getFundedAmountKobo() - amountKobo));
    if (loan.getStatus() == LoanStatus.FUNDED && loan.getFundedAmountKobo() < loan.getAmountKobo()) {
      loan.setStatus(LoanStatus.FUNDING);
    }
    loan.setUpdatedAt(Instant.now());
    loanApplicationRepository.save(loan);
  }

  @Override
  @Transactional
  public void revertToFunded(UUID loanId) {
    Optional<LoanApplication> maybeLoan = loanApplicationRepository.findById(loanId);
    if (maybeLoan.isEmpty()) {
      log.warn("investment.disbursement.compensate for unknown loan {}", loanId);
      return;
    }
    LoanApplication loan = maybeLoan.get();
    if (loan.getStatus() != LoanStatus.DISBURSING) {
      log.info("Ignoring disbursement-compensate for loan {} in status {}", loanId, loan.getStatus());
      return;
    }
    loan.setStatus(LoanStatus.FUNDED);
    loan.setUpdatedAt(Instant.now());
    loanApplicationRepository.save(loan);
    log.warn("Loan {} disbursement failed and was compensated; reverted to FUNDED", loanId);
  }

  @Override
  @Transactional
  public void disburse(UUID loanId) {
    LoanApplication loan =
        loanApplicationRepository
            .findById(loanId)
            .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Loan request not found"));
    if (loan.getStatus() != LoanStatus.DISBURSING) {
      log.info("Ignoring ledger.loan-disbursement.completed for loan {} in status {}", loanId, loan.getStatus());
      return;
    }

    Instant now = Instant.now();
    ScheduleResponse schedule =
        RepaymentScheduleCalculator.compute(
            ScheduleCalculationRequest.builder()
                .amountKobo(loan.getAmountKobo())
                .tenorMonths(loan.getTenorMonths())
                .interestRateBps(loan.getInterestRateBps())
                .startDate(now.atZone(ZoneOffset.UTC).toLocalDate())
                .build());

    repaymentScheduleRepository.save(
        RepaymentSchedule.builder()
            .loanId(loanId)
            .totalPrincipalKobo(schedule.getTotalPrincipalKobo())
            .totalInterestKobo(schedule.getTotalInterestKobo())
            .totalRepaymentKobo(schedule.getTotalRepaymentKobo())
            .createdAt(now)
            .build());
    instalmentRepository.saveAll(
        schedule.getInstalments().stream()
            .map(
                i ->
                    Instalment.builder()
                        .id(UuidV7.generate())
                        .loanId(loanId)
                        .instalmentNumber(i.getInstalmentNumber())
                        .dueDate(i.getDueDate())
                        .principalKobo(i.getPrincipalKobo())
                        .interestKobo(i.getInterestKobo())
                        .totalKobo(i.getTotalKobo())
                        .status(InstalmentStatus.PENDING)
                        .build())
            .toList());

    loan.setStatus(LoanStatus.ACTIVE);
    loan.setUpdatedAt(now);

    outboxEventWriter.write(
        "LoanApplication",
        loan.getId().toString(),
        KafkaTopics.LOAN_DISBURSED,
        LoanDisbursedPayload.builder()
            .loanId(loan.getId())
            .borrowerUserId(loan.getBorrowerUserId())
            .amountKobo(loan.getAmountKobo())
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    loanApplicationRepository.save(loan);
  }

  @Override
  @Transactional
  public LoanResponse requestRepayment(UUID borrowerUserId, UUID loanId, RepaymentRequest request) {
    if (request.getMethod() != RepaymentMethod.WALLET) {
      throw new UnprocessableEntityException(
          "REPAYMENT_METHOD_NOT_SUPPORTED", "Only wallet-funded repayments are supported");
    }
    LoanApplication loan = findOwned(borrowerUserId, loanId);
    if (loan.getStatus() != LoanStatus.ACTIVE) {
      throw new ConflictException("LOAN_NOT_ACTIVE", "Repayments can only be made while the loan is Active");
    }
    Instalment instalment =
        instalmentRepository
            .findFirstByLoanIdAndStatusOrderByInstalmentNumberAsc(loanId, InstalmentStatus.PENDING)
            .orElseThrow(
                () -> new ConflictException("LOAN_FULLY_REPAID", "This loan has no outstanding instalments"));

    Instant now = Instant.now();
    instalment.setStatus(InstalmentStatus.REQUESTED);
    instalmentRepository.save(instalment);

    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.LOAN_REPAYMENT_REQUESTED,
        LoanRepaymentRequestedPayload.builder()
            .loanId(loanId)
            .instalmentId(instalment.getId())
            .borrowerUserId(borrowerUserId)
            .amountKobo(instalment.getTotalKobo())
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    return toResponse(loan);
  }

  @Override
  @Transactional
  public void markInstalmentPaid(UUID loanId, UUID instalmentId) {
    Instalment instalment = instalmentRepository.findById(instalmentId).orElse(null);
    if (instalment == null || instalment.getStatus() != InstalmentStatus.REQUESTED) {
      log.info("Ignoring repayment-completed for instalment {} (not REQUESTED)", instalmentId);
      return;
    }

    Instant now = Instant.now();
    instalment.setStatus(InstalmentStatus.PAID);
    instalment.setPaidAt(now);
    instalmentRepository.save(instalment);

    if (!instalmentRepository.existsByLoanIdAndStatusNot(loanId, InstalmentStatus.PAID)) {
      LoanApplication loan =
          loanApplicationRepository
              .findById(loanId)
              .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Loan request not found"));
      loan.setStatus(LoanStatus.REPAID);
      loan.setUpdatedAt(now);
      loanApplicationRepository.save(loan);

      outboxEventWriter.write(
          "LoanApplication",
          loanId.toString(),
          KafkaTopics.LOAN_REPAID,
          LoanRepaidPayload.builder()
              .loanId(loanId)
              .borrowerUserId(loan.getBorrowerUserId())
              .occurredAtEpochMillis(now.toEpochMilli())
              .build());
    }
  }

  @Override
  @Transactional
  public void revertInstalmentToPending(UUID loanId, UUID instalmentId) {
    Instalment instalment = instalmentRepository.findById(instalmentId).orElse(null);
    if (instalment == null || instalment.getStatus() != InstalmentStatus.REQUESTED) {
      log.info("Ignoring repayment-failure for instalment {} (not REQUESTED)", instalmentId);
      return;
    }
    instalment.setStatus(InstalmentStatus.PENDING);
    instalmentRepository.save(instalment);
    log.warn("Repayment failed for loan {} instalment {}; reverted to PENDING", loanId, instalmentId);
  }

  private void enforceTrustTierLimit(long amountKobo) {
    KycStatusResponse kycStatus = kycGateway.fetchMyStatus();
    long limitKobo =
        switch (kycStatus.getTrustTier() == null ? "" : kycStatus.getTrustTier()) {
          case "TIER_1" -> tier1LimitKobo;
          case "TIER_2" -> tier2LimitKobo;
          case "TIER_3" -> tier3LimitKobo;
          default ->
              throw new UnprocessableEntityException(
                  "KYC_NOT_VERIFIED", "You must complete KYC verification before applying for a loan");
        };
    if (amountKobo > limitKobo) {
      throw new UnprocessableEntityException(
          "LOAN_AMOUNT_EXCEEDS_TRUST_TIER_LIMIT",
          "Requested amount exceeds the borrowing limit for your trust tier (" + limitKobo + " kobo)");
    }
  }

  private LoanApplication findOwned(UUID borrowerUserId, UUID loanId) {
    LoanApplication loan =
        loanApplicationRepository.findById(loanId).orElseThrow(() -> notFound());
    if (!loan.getBorrowerUserId().equals(borrowerUserId)) {
      throw notFound();
    }
    return loan;
  }

  private NotFoundException notFound() {
    return new NotFoundException("LOAN_NOT_FOUND", "Loan request not found");
  }

  private LoanResponse toResponse(LoanApplication loan) {
    return loanMapper.toResponse(loan, scheduleFor(loan));
  }

  /** Once a loan has disbursed, the real persisted schedule (and every instalment's real
   * {@code PAID}/{@code REQUESTED} status) is the truth — falling back to a freshly recomputed
   * preview would silently show a stale or wrong picture once repayments start happening. Before
   * disbursement, no persisted schedule exists yet, so the preview is all there is. */
  private ScheduleResponse scheduleFor(LoanApplication loan) {
    return repaymentScheduleRepository
        .findById(loan.getId())
        .map(schedule -> persistedScheduleResponse(loan.getId(), schedule))
        .orElseGet(() -> schedulePreviewFor(loan));
  }

  private ScheduleResponse persistedScheduleResponse(UUID loanId, RepaymentSchedule schedule) {
    List<Instalment> instalments = instalmentRepository.findAllByLoanIdOrderByInstalmentNumberAsc(loanId);
    long outstanding = schedule.getTotalPrincipalKobo();
    List<InstalmentResponse> instalmentResponses = new ArrayList<>(instalments.size());
    for (Instalment instalment : instalments) {
      outstanding -= instalment.getPrincipalKobo();
      instalmentResponses.add(
          InstalmentResponse.builder()
              .instalmentNumber(instalment.getInstalmentNumber())
              .dueDate(instalment.getDueDate())
              .principalKobo(instalment.getPrincipalKobo())
              .interestKobo(instalment.getInterestKobo())
              .totalKobo(instalment.getTotalKobo())
              .outstandingBalanceKobo(Math.max(outstanding, 0))
              .status(instalment.getStatus())
              .paidAt(instalment.getPaidAt())
              .build());
    }
    return ScheduleResponse.builder()
        .totalPrincipalKobo(schedule.getTotalPrincipalKobo())
        .totalInterestKobo(schedule.getTotalInterestKobo())
        .totalRepaymentKobo(schedule.getTotalRepaymentKobo())
        .instalments(instalmentResponses)
        .build();
  }

  private ScheduleResponse schedulePreviewFor(LoanApplication loan) {
    return RepaymentScheduleCalculator.compute(
        ScheduleCalculationRequest.builder()
            .amountKobo(loan.getAmountKobo())
            .tenorMonths(loan.getTenorMonths())
            .interestRateBps(loan.getInterestRateBps())
            .startDate(loan.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate())
            .build());
  }
}

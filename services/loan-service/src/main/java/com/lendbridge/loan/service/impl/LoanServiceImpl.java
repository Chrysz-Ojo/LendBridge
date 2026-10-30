package com.lendbridge.loan.service.impl;

import com.lendbridge.loan.document.AgreementPdfRenderer;
import com.lendbridge.loan.document.DocumentUploadClient;
import com.lendbridge.loan.document.DocumentUploadResponse;
import com.lendbridge.loan.dto.AgreementResponse;
import com.lendbridge.loan.dto.CreateLoanRequest;
import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.ScheduleResponse;
import com.lendbridge.loan.kyc.KycGateway;
import com.lendbridge.loan.kyc.KycStatusResponse;
import com.lendbridge.loan.mapper.LoanMapper;
import com.lendbridge.loan.model.LoanApplication;
import com.lendbridge.loan.model.LoanStatus;
import com.lendbridge.loan.outbox.KafkaTopics;
import com.lendbridge.loan.outbox.LoanSubmittedPayload;
import com.lendbridge.loan.repository.LoanApplicationRepository;
import com.lendbridge.loan.service.LoanService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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

  private final LoanApplicationRepository loanApplicationRepository;
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

  public LoanServiceImpl(
      LoanApplicationRepository loanApplicationRepository,
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
      @Value("${loan.trust-tier.tier3-limit-kobo}") long tier3LimitKobo) {
    this.loanApplicationRepository = loanApplicationRepository;
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
      loanApplicationRepository.save(loan);
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
    loan.setStatus(LoanStatus.CANCELLED);
    loan.setReviewRequired(false);
    loan.setUpdatedAt(Instant.now());
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
    return loanMapper.toResponse(loan, schedulePreviewFor(loan));
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

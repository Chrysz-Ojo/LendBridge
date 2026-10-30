package com.lendbridge.loan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.loan.document.AgreementPdfRenderer;
import com.lendbridge.loan.document.DocumentUploadClient;
import com.lendbridge.loan.document.DocumentUploadResponse;
import com.lendbridge.loan.dto.AgreementResponse;
import com.lendbridge.loan.dto.CreateLoanRequest;
import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.kyc.KycGateway;
import com.lendbridge.loan.kyc.KycStatusResponse;
import com.lendbridge.loan.mapper.LoanMapper;
import com.lendbridge.loan.model.LoanApplication;
import com.lendbridge.loan.model.LoanPurpose;
import com.lendbridge.loan.model.LoanStatus;
import com.lendbridge.loan.repository.LoanApplicationRepository;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class LoanServiceImplTest {

  private LoanApplicationRepository repository;
  private KycGateway kycGateway;
  private OutboxEventWriter outboxEventWriter;
  private AgreementPdfRenderer agreementPdfRenderer;
  private DocumentUploadClient documentUploadClient;
  private LoanServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = mock(LoanApplicationRepository.class);
    kycGateway = mock(KycGateway.class);
    outboxEventWriter = mock(OutboxEventWriter.class);
    agreementPdfRenderer = mock(AgreementPdfRenderer.class);
    documentUploadClient = mock(DocumentUploadClient.class);
    service =
        new LoanServiceImpl(
            repository,
            kycGateway,
            outboxEventWriter,
            agreementPdfRenderer,
            documentUploadClient,
            new LoanMapper(),
            1,
            24,
            500,
            6000,
            20_000_000L,
            100_000_000L,
            500_000_000L);
  }

  private CreateLoanRequest request(long amountKobo, int tenorMonths, int rateBps) {
    return new CreateLoanRequest(amountKobo, tenorMonths, LoanPurpose.BUSINESS, "stock", rateBps);
  }

  @Test
  void create_withinTrustTierLimit_savesAsDraft() {
    when(kycGateway.fetchMyStatus()).thenReturn(new KycStatusResponse("VERIFIED", "TIER_2"));
    UUID borrowerId = UUID.randomUUID();

    LoanResponse response = service.create(borrowerId, request(5_000_000L, 12, 1800));

    assertThat(response.getStatus()).isEqualTo(LoanStatus.DRAFT);
    assertThat(response.getSchedule().getInstalments()).hasSize(12);
    verify(repository).saveAndFlush(any(LoanApplication.class));
  }

  @Test
  void create_amountExceedsTrustTierLimit_throwsUnprocessableEntity() {
    when(kycGateway.fetchMyStatus()).thenReturn(new KycStatusResponse("VERIFIED", "TIER_1"));

    assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(50_000_000L, 12, 1800)))
        .isInstanceOf(UnprocessableEntityException.class)
        .hasMessageContaining("trust tier");
  }

  @Test
  void create_borrowerNotKycVerified_throwsUnprocessableEntity() {
    when(kycGateway.fetchMyStatus()).thenReturn(new KycStatusResponse(null, null));

    assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(1_000_000L, 12, 1800)))
        .isInstanceOf(UnprocessableEntityException.class);
  }

  @Test
  void create_tenorOutOfRange_throwsBadRequestBeforeCheckingKyc() {
    assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(1_000_000L, 36, 1800)))
        .isInstanceOf(BadRequestException.class);
    verifyNoInteractions(kycGateway);
  }

  @Test
  void create_rateOutOfRange_throwsBadRequest() {
    assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(1_000_000L, 12, 100)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void create_duplicateOpenRequest_mapsConstraintViolationToConflict() {
    when(kycGateway.fetchMyStatus()).thenReturn(new KycStatusResponse("VERIFIED", "TIER_2"));
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(1_000_000L, 12, 1800)))
        .isInstanceOf(ConflictException.class);
  }

  private LoanApplication loan(UUID borrowerId, LoanStatus status) {
    Instant now = Instant.now();
    return LoanApplication.builder()
        .id(UUID.randomUUID())
        .borrowerUserId(borrowerId)
        .amountKobo(1_000_000L)
        .tenorMonths(12)
        .purpose(LoanPurpose.BUSINESS)
        .interestRateBps(1800)
        .status(status)
        .reviewRequired(false)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Test
  void submit_fromDraft_movesToUnderReviewAndPublishesEvent() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.DRAFT);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    LoanResponse response = service.submit(borrowerId, loan.getId());

    assertThat(response.getStatus()).isEqualTo(LoanStatus.UNDER_REVIEW);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void submit_notInDraft_throwsConflict() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.LISTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(() -> service.submit(borrowerId, loan.getId())).isInstanceOf(ConflictException.class);
  }

  @Test
  void submit_loanOwnedByAnotherBorrower_throwsNotFound() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.DRAFT);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(() -> service.submit(borrowerId, loan.getId())).isInstanceOf(NotFoundException.class);
  }

  @Test
  void cancel_fromListed_succeeds() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.LISTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    LoanResponse response = service.cancel(borrowerId, loan.getId());

    assertThat(response.getStatus()).isEqualTo(LoanStatus.CANCELLED);
  }

  @Test
  void cancel_fromListed_publishesDelistedEvent() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.LISTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.cancel(borrowerId, loan.getId());

    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void cancel_fromDraft_doesNotPublishDelistedEvent() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.DRAFT);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.cancel(borrowerId, loan.getId());

    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void cancel_fromRejected_throwsConflict() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.REJECTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(() -> service.cancel(borrowerId, loan.getId())).isInstanceOf(ConflictException.class);
  }

  @Test
  void generateAgreement_loanNotListed_throwsConflict() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.DRAFT);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(() -> service.generateAgreement(borrowerId, loan.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void generateAgreement_firstCall_rendersAndUploads() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.LISTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));
    when(agreementPdfRenderer.render(loan)).thenReturn(new byte[] {1, 2, 3});
    UUID documentId = UUID.randomUUID();
    when(documentUploadClient.uploadAgreement(any(), any())).thenReturn(new DocumentUploadResponse(documentId));
    when(agreementPdfRenderer.templateVersion()).thenReturn(1);

    AgreementResponse response = service.generateAgreement(borrowerId, loan.getId());

    assertThat(response.getDocumentId()).isEqualTo(documentId);
    verify(documentUploadClient).uploadAgreement(any(), any());
  }

  @Test
  void generateAgreement_alreadyGenerated_returnsExistingWithoutReUploading() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.LISTED);
    UUID existingDocumentId = UUID.randomUUID();
    loan.setAgreementDocumentId(existingDocumentId);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));
    when(agreementPdfRenderer.templateVersion()).thenReturn(1);

    AgreementResponse response = service.generateAgreement(borrowerId, loan.getId());

    assertThat(response.getDocumentId()).isEqualTo(existingDocumentId);
    verifyNoInteractions(documentUploadClient);
  }
}

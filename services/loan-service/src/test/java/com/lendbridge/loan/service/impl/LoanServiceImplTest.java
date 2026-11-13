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
import com.lendbridge.loan.dto.RepaymentRequest;
import com.lendbridge.loan.model.Instalment;
import com.lendbridge.loan.model.InstalmentStatus;
import com.lendbridge.loan.model.LoanApplication;
import com.lendbridge.loan.model.LoanPurpose;
import com.lendbridge.loan.model.LoanStatus;
import com.lendbridge.loan.model.RepaymentMethod;
import com.lendbridge.loan.model.RepaymentSchedule;
import com.lendbridge.loan.repository.InstalmentRepository;
import com.lendbridge.loan.repository.LoanApplicationRepository;
import com.lendbridge.loan.repository.RepaymentScheduleRepository;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class LoanServiceImplTest {

  private LoanApplicationRepository repository;
  private RepaymentScheduleRepository repaymentScheduleRepository;
  private InstalmentRepository instalmentRepository;
  private KycGateway kycGateway;
  private OutboxEventWriter outboxEventWriter;
  private AgreementPdfRenderer agreementPdfRenderer;
  private DocumentUploadClient documentUploadClient;
  private LoanServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = mock(LoanApplicationRepository.class);
    repaymentScheduleRepository = mock(RepaymentScheduleRepository.class);
    instalmentRepository = mock(InstalmentRepository.class);
    kycGateway = mock(KycGateway.class);
    outboxEventWriter = mock(OutboxEventWriter.class);
    agreementPdfRenderer = mock(AgreementPdfRenderer.class);
    documentUploadClient = mock(DocumentUploadClient.class);
    service =
        new LoanServiceImpl(
            repository,
            repaymentScheduleRepository,
            instalmentRepository,
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
            500_000_000L,
            100);
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

  @Test
  void signAgreement_whenFunded_computesFeeAndMovesToDisbursing() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.FUNDED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    LoanResponse response = service.signAgreement(borrowerId, loan.getId());

    assertThat(response.getStatus()).isEqualTo(LoanStatus.DISBURSING);
    assertThat(response.getOriginationFeeKobo()).isEqualTo(10_000L); // 1% of 1_000_000
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void signAgreement_whenNotFunded_throwsConflict() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.LISTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(() -> service.signAgreement(borrowerId, loan.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void recordCommitment_firstCommitment_movesListedToFunding() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.LISTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.recordCommitment(loan.getId(), 400_000L);

    assertThat(loan.getStatus()).isEqualTo(LoanStatus.FUNDING);
    assertThat(loan.getFundedAmountKobo()).isEqualTo(400_000L);
  }

  @Test
  void recordCommitment_reachesFullAmount_movesToFunded() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.FUNDING);
    loan.setFundedAmountKobo(400_000L);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.recordCommitment(loan.getId(), 600_000L);

    assertThat(loan.getStatus()).isEqualTo(LoanStatus.FUNDED);
    assertThat(loan.getFundedAmountKobo()).isEqualTo(1_000_000L);
  }

  @Test
  void recordCommitment_loanAlreadyDisbursing_isIgnored() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.DISBURSING);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.recordCommitment(loan.getId(), 100_000L);

    assertThat(loan.getFundedAmountKobo()).isEqualTo(0L);
    verify(repository, never()).save(any());
  }

  @Test
  void recordCommitmentCancelled_dropsBelowFull_revertsFundedToFunding() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.FUNDED);
    loan.setFundedAmountKobo(1_000_000L);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.recordCommitmentCancelled(loan.getId(), 300_000L);

    assertThat(loan.getStatus()).isEqualTo(LoanStatus.FUNDING);
    assertThat(loan.getFundedAmountKobo()).isEqualTo(700_000L);
  }

  @Test
  void revertToFunded_whenDisbursing_revertsToFunded() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.DISBURSING);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.revertToFunded(loan.getId());

    assertThat(loan.getStatus()).isEqualTo(LoanStatus.FUNDED);
  }

  @Test
  void revertToFunded_whenNotDisbursing_isIgnored() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.ACTIVE);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.revertToFunded(loan.getId());

    assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    verify(repository, never()).save(any());
  }

  @Test
  void disburse_whenDisbursing_persistsScheduleAndMovesToActive() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.DISBURSING);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.disburse(loan.getId());

    assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    verify(repaymentScheduleRepository).save(any(RepaymentSchedule.class));
    ArgumentCaptor<List<Instalment>> captor = ArgumentCaptor.forClass(List.class);
    verify(instalmentRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(12);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void disburse_loanNotDisbursing_isIgnored() {
    LoanApplication loan = loan(UUID.randomUUID(), LoanStatus.ACTIVE);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.disburse(loan.getId());

    verifyNoInteractions(repaymentScheduleRepository, instalmentRepository);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  private Instalment instalment(UUID loanId, int number, InstalmentStatus status) {
    return Instalment.builder()
        .id(UUID.randomUUID())
        .loanId(loanId)
        .instalmentNumber(number)
        .dueDate(LocalDate.now().plusMonths(number))
        .principalKobo(80_000L)
        .interestKobo(5_000L)
        .totalKobo(85_000L)
        .status(status)
        .build();
  }

  @Test
  void requestRepayment_whenActive_movesNextPendingInstalmentToRequested() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.ACTIVE);
    Instalment next = instalment(loan.getId(), 1, InstalmentStatus.PENDING);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));
    when(instalmentRepository.findFirstByLoanIdAndStatusOrderByInstalmentNumberAsc(loan.getId(), InstalmentStatus.PENDING))
        .thenReturn(Optional.of(next));

    service.requestRepayment(borrowerId, loan.getId(), new RepaymentRequest(RepaymentMethod.WALLET));

    assertThat(next.getStatus()).isEqualTo(InstalmentStatus.REQUESTED);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void requestRepayment_methodNotWallet_throwsUnprocessableEntity() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.ACTIVE);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(
            () -> service.requestRepayment(borrowerId, loan.getId(), new RepaymentRequest(RepaymentMethod.CARD)))
        .isInstanceOf(UnprocessableEntityException.class);
    verifyNoInteractions(instalmentRepository);
  }

  @Test
  void requestRepayment_loanNotActive_throwsConflict() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.DISBURSING);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(
            () -> service.requestRepayment(borrowerId, loan.getId(), new RepaymentRequest(RepaymentMethod.WALLET)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void requestRepayment_noOutstandingInstalments_throwsConflict() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.ACTIVE);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));
    when(instalmentRepository.findFirstByLoanIdAndStatusOrderByInstalmentNumberAsc(loan.getId(), InstalmentStatus.PENDING))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.requestRepayment(borrowerId, loan.getId(), new RepaymentRequest(RepaymentMethod.WALLET)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void markInstalmentPaid_notLastInstalment_staysActive() {
    UUID loanId = UUID.randomUUID();
    Instalment instalment = instalment(loanId, 1, InstalmentStatus.REQUESTED);
    when(instalmentRepository.findById(instalment.getId())).thenReturn(Optional.of(instalment));
    when(instalmentRepository.existsByLoanIdAndStatusNot(loanId, InstalmentStatus.PAID)).thenReturn(true);

    service.markInstalmentPaid(loanId, instalment.getId());

    assertThat(instalment.getStatus()).isEqualTo(InstalmentStatus.PAID);
    assertThat(instalment.getPaidAt()).isNotNull();
    verify(repository, never()).findById(loanId);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void markInstalmentPaid_lastInstalment_movesLoanToRepaid() {
    UUID borrowerId = UUID.randomUUID();
    LoanApplication loan = loan(borrowerId, LoanStatus.ACTIVE);
    Instalment instalment = instalment(loan.getId(), 12, InstalmentStatus.REQUESTED);
    when(instalmentRepository.findById(instalment.getId())).thenReturn(Optional.of(instalment));
    when(instalmentRepository.existsByLoanIdAndStatusNot(loan.getId(), InstalmentStatus.PAID)).thenReturn(false);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    service.markInstalmentPaid(loan.getId(), instalment.getId());

    assertThat(loan.getStatus()).isEqualTo(LoanStatus.REPAID);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void markInstalmentPaid_instalmentNotRequested_isIgnored() {
    UUID loanId = UUID.randomUUID();
    Instalment instalment = instalment(loanId, 1, InstalmentStatus.PENDING);
    when(instalmentRepository.findById(instalment.getId())).thenReturn(Optional.of(instalment));

    service.markInstalmentPaid(loanId, instalment.getId());

    assertThat(instalment.getStatus()).isEqualTo(InstalmentStatus.PENDING);
    verify(instalmentRepository, never()).save(any());
  }

  @Test
  void revertInstalmentToPending_whenRequested_revertsToPending() {
    UUID loanId = UUID.randomUUID();
    Instalment instalment = instalment(loanId, 1, InstalmentStatus.REQUESTED);
    when(instalmentRepository.findById(instalment.getId())).thenReturn(Optional.of(instalment));

    service.revertInstalmentToPending(loanId, instalment.getId());

    assertThat(instalment.getStatus()).isEqualTo(InstalmentStatus.PENDING);
  }

  @Test
  void revertInstalmentToPending_whenNotRequested_isIgnored() {
    UUID loanId = UUID.randomUUID();
    Instalment instalment = instalment(loanId, 1, InstalmentStatus.PAID);
    when(instalmentRepository.findById(instalment.getId())).thenReturn(Optional.of(instalment));

    service.revertInstalmentToPending(loanId, instalment.getId());

    assertThat(instalment.getStatus()).isEqualTo(InstalmentStatus.PAID);
    verify(instalmentRepository, never()).save(any());
  }
}

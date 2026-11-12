package com.lendbridge.messaging.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.messaging.dto.AbuseReportRequest;
import com.lendbridge.messaging.dto.AbuseReportResponse;
import com.lendbridge.messaging.model.AbuseReport;
import com.lendbridge.messaging.model.AbuseReportStatus;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.repository.AbuseReportRepository;
import com.lendbridge.messaging.repository.ConversationRepository;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbuseReportServiceImplTest {

  private AbuseReportRepository abuseReportRepository;
  private ConversationRepository conversationRepository;
  private AbuseReportServiceImpl service;

  @BeforeEach
  void setUp() {
    abuseReportRepository = mock(AbuseReportRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    service = new AbuseReportServiceImpl(abuseReportRepository, conversationRepository);
  }

  @Test
  void file_participantReports_createsOpenReport() {
    UUID conversationId = UUID.randomUUID();
    UUID reporterUserId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId))
        .thenReturn(
            Optional.of(
                Conversation.builder()
                    .id(conversationId)
                    .borrowerUserId(reporterUserId)
                    .lenderUserId(UUID.randomUUID())
                    .build()));

    AbuseReportResponse response =
        service.file(conversationId, AbuseReportRequest.builder().reason("spam").build(), reporterUserId);

    assertThat(response.getStatus()).isEqualTo(AbuseReportStatus.OPEN);
    assertThat(response.getReason()).isEqualTo("spam");
    verify(abuseReportRepository).save(any());
  }

  @Test
  void file_nonParticipant_throwsUnauthorized() {
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId))
        .thenReturn(
            Optional.of(
                Conversation.builder()
                    .id(conversationId)
                    .borrowerUserId(UUID.randomUUID())
                    .lenderUserId(UUID.randomUUID())
                    .build()));

    assertThatThrownBy(
            () ->
                service.file(
                    conversationId, AbuseReportRequest.builder().reason("spam").build(), UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void findOpen_returnsOnlyOpenReports() {
    AbuseReport report =
        AbuseReport.builder()
            .id(UUID.randomUUID())
            .conversationId(UUID.randomUUID())
            .reporterUserId(UUID.randomUUID())
            .reason("spam")
            .status(AbuseReportStatus.OPEN)
            .createdAt(Instant.now())
            .build();
    when(abuseReportRepository.findByStatusOrderByCreatedAtAsc(AbuseReportStatus.OPEN)).thenReturn(List.of(report));

    List<AbuseReportResponse> result = service.findOpen();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(AbuseReportStatus.OPEN);
  }

  @Test
  void resolve_marksReportResolved() {
    UUID reportId = UUID.randomUUID();
    AbuseReport report =
        AbuseReport.builder()
            .id(reportId)
            .conversationId(UUID.randomUUID())
            .reporterUserId(UUID.randomUUID())
            .reason("spam")
            .status(AbuseReportStatus.OPEN)
            .createdAt(Instant.now())
            .build();
    when(abuseReportRepository.findById(reportId)).thenReturn(Optional.of(report));

    AbuseReportResponse response = service.resolve(reportId);

    assertThat(response.getStatus()).isEqualTo(AbuseReportStatus.RESOLVED);
    ArgumentCaptor<AbuseReport> captor = ArgumentCaptor.forClass(AbuseReport.class);
    verify(abuseReportRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(AbuseReportStatus.RESOLVED);
  }

  @Test
  void resolve_unknownReport_throwsNotFound() {
    UUID reportId = UUID.randomUUID();
    when(abuseReportRepository.findById(reportId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolve(reportId)).isInstanceOf(NotFoundException.class);
  }
}

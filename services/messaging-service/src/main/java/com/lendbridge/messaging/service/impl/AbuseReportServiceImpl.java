package com.lendbridge.messaging.service.impl;

import com.lendbridge.messaging.dto.AbuseReportRequest;
import com.lendbridge.messaging.dto.AbuseReportResponse;
import com.lendbridge.messaging.model.AbuseReport;
import com.lendbridge.messaging.model.AbuseReportStatus;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.repository.AbuseReportRepository;
import com.lendbridge.messaging.repository.ConversationRepository;
import com.lendbridge.messaging.service.AbuseReportService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AbuseReportServiceImpl implements AbuseReportService {

  private final AbuseReportRepository abuseReportRepository;
  private final ConversationRepository conversationRepository;

  public AbuseReportServiceImpl(
      AbuseReportRepository abuseReportRepository, ConversationRepository conversationRepository) {
    this.abuseReportRepository = abuseReportRepository;
    this.conversationRepository = conversationRepository;
  }

  @Override
  @Transactional
  public AbuseReportResponse file(UUID conversationId, AbuseReportRequest request, UUID reporterUserId) {
    Conversation conversation =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new NotFoundException("CONVERSATION_NOT_FOUND", "Conversation not found"));
    if (!conversation.getBorrowerUserId().equals(reporterUserId)
        && !conversation.getLenderUserId().equals(reporterUserId)) {
      throw new UnauthorizedException("NOT_A_PARTICIPANT", "You are not a participant in this conversation");
    }

    AbuseReport report =
        AbuseReport.builder()
            .id(UuidV7.generate())
            .conversationId(conversationId)
            .reporterUserId(reporterUserId)
            .reason(request.getReason())
            .status(AbuseReportStatus.OPEN)
            .createdAt(Instant.now())
            .build();
    abuseReportRepository.save(report);
    return toResponse(report);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AbuseReportResponse> findOpen() {
    return abuseReportRepository.findByStatusOrderByCreatedAtAsc(AbuseReportStatus.OPEN).stream()
        .map(AbuseReportServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public AbuseReportResponse resolve(UUID reportId) {
    AbuseReport report =
        abuseReportRepository
            .findById(reportId)
            .orElseThrow(() -> new NotFoundException("ABUSE_REPORT_NOT_FOUND", "Abuse report not found"));
    report.setStatus(AbuseReportStatus.RESOLVED);
    abuseReportRepository.save(report);
    return toResponse(report);
  }

  private static AbuseReportResponse toResponse(AbuseReport report) {
    return AbuseReportResponse.builder()
        .id(report.getId())
        .conversationId(report.getConversationId())
        .reporterUserId(report.getReporterUserId())
        .reason(report.getReason())
        .status(report.getStatus())
        .createdAt(report.getCreatedAt())
        .build();
  }
}

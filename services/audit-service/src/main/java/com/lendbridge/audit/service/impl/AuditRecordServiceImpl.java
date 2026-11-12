package com.lendbridge.audit.service.impl;

import com.lendbridge.audit.dto.AuditRecordQueryRequest;
import com.lendbridge.audit.dto.AuditRecordResponse;
import com.lendbridge.audit.dto.RecordAuditEventRequest;
import com.lendbridge.audit.model.AuditRecord;
import com.lendbridge.audit.repository.AuditRecordRepository;
import com.lendbridge.audit.service.AuditRecordService;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditRecordServiceImpl implements AuditRecordService {

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private final AuditRecordRepository auditRecordRepository;

  public AuditRecordServiceImpl(AuditRecordRepository auditRecordRepository) {
    this.auditRecordRepository = auditRecordRepository;
  }

  @Override
  @Transactional
  public void record(RecordAuditEventRequest request) {
    Instant now = Instant.now();
    auditRecordRepository.save(
        AuditRecord.builder()
            .id(UuidV7.generate())
            .topic(request.getTopic())
            .aggregateKey(request.getAggregateKey())
            .payload(request.getPayload())
            .occurredAt(request.getOccurredAt())
            .recordedAt(now)
            .kafkaPartition(request.getKafkaPartition())
            .kafkaOffset(request.getKafkaOffset())
            .build());
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditRecordResponse> query(AuditRecordQueryRequest request) {
    int limit = request.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(request.getLimit(), MAX_LIMIT);
    return auditRecordRepository
        .search(
            request.getTopic(),
            request.getAggregateKey(),
            request.getFrom(),
            request.getTo(),
            PageRequest.of(0, limit))
        .stream()
        .map(AuditRecordServiceImpl::toResponse)
        .toList();
  }

  private static AuditRecordResponse toResponse(AuditRecord record) {
    return AuditRecordResponse.builder()
        .id(record.getId())
        .topic(record.getTopic())
        .aggregateKey(record.getAggregateKey())
        .payload(record.getPayload())
        .occurredAt(record.getOccurredAt())
        .recordedAt(record.getRecordedAt())
        .build();
  }
}

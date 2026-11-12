package com.lendbridge.audit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.audit.dto.AuditRecordQueryRequest;
import com.lendbridge.audit.dto.RecordAuditEventRequest;
import com.lendbridge.audit.model.AuditRecord;
import com.lendbridge.audit.repository.AuditRecordRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class AuditRecordServiceImplTest {

  private AuditRecordRepository auditRecordRepository;
  private AuditRecordServiceImpl service;

  @BeforeEach
  void setUp() {
    auditRecordRepository = mock(AuditRecordRepository.class);
    service = new AuditRecordServiceImpl(auditRecordRepository);
  }

  @Test
  void record_savesAuditRecordWithGivenFields() {
    Instant occurredAt = Instant.parse("2026-11-12T10:00:00Z");
    RecordAuditEventRequest request =
        RecordAuditEventRequest.builder()
            .topic("loan.submitted")
            .aggregateKey("loan-123")
            .payload("{\"loanId\": \"loan-123\"}")
            .occurredAt(occurredAt)
            .kafkaPartition(0)
            .kafkaOffset(42L)
            .build();

    service.record(request);

    ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
    verify(auditRecordRepository).save(captor.capture());
    AuditRecord saved = captor.getValue();
    assertThat(saved.getTopic()).isEqualTo("loan.submitted");
    assertThat(saved.getAggregateKey()).isEqualTo("loan-123");
    assertThat(saved.getPayload()).isEqualTo("{\"loanId\": \"loan-123\"}");
    assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(saved.getKafkaPartition()).isZero();
    assertThat(saved.getKafkaOffset()).isEqualTo(42L);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getRecordedAt()).isNotNull();
  }

  @Test
  void query_defaultsLimitWhenNotProvided() {
    when(auditRecordRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

    service.query(AuditRecordQueryRequest.builder().build());

    verify(auditRecordRepository)
        .search(isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 100)));
  }

  @Test
  void query_capsLimitAtMaximum() {
    when(auditRecordRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

    service.query(AuditRecordQueryRequest.builder().limit(10_000).build());

    verify(auditRecordRepository)
        .search(isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 500)));
  }

  @Test
  void query_mapsRepositoryResultsToResponses() {
    AuditRecord record =
        AuditRecord.builder()
            .id(java.util.UUID.randomUUID())
            .topic("kyc.verified")
            .aggregateKey("user-1")
            .payload("{}")
            .occurredAt(Instant.now())
            .recordedAt(Instant.now())
            .kafkaPartition(0)
            .kafkaOffset(1L)
            .build();
    when(auditRecordRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(record));

    var result = service.query(AuditRecordQueryRequest.builder().topic("kyc.verified").build());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getTopic()).isEqualTo("kyc.verified");
  }
}

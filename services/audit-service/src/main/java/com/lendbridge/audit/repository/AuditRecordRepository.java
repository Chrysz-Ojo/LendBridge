package com.lendbridge.audit.repository;

import com.lendbridge.audit.model.AuditRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

  /** Every filter is optional (null = no constraint on that column) — backs
   * {@code AuditRecordController}'s query API, where topic/aggregateKey/time-range can each be
   * supplied independently or not at all. */
  @Query(
      """
      SELECT a FROM AuditRecord a
      WHERE (:topic IS NULL OR a.topic = :topic)
        AND (:aggregateKey IS NULL OR a.aggregateKey = :aggregateKey)
        AND (:from IS NULL OR a.occurredAt >= :from)
        AND (:to IS NULL OR a.occurredAt <= :to)
      ORDER BY a.occurredAt DESC
      """)
  List<AuditRecord> search(
      @Param("topic") String topic,
      @Param("aggregateKey") String aggregateKey,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}

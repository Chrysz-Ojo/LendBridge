package com.lendbridge.reporting.repository;

import com.lendbridge.reporting.model.UserSignupFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSignupFactRepository extends JpaRepository<UserSignupFact, UUID> {

  /** Raw rows in range, grouped into per-day counts in {@code ReportingQueryServiceImpl} rather
   * than via a dialect-specific SQL date-truncation function — keeps this query portable and
   * avoids depending on exactly what type Hibernate hands back from a {@code date_trunc} call. */
  @Query(
      """
      SELECT f.occurredAt FROM UserSignupFact f
      WHERE (:from IS NULL OR f.occurredAt >= :from) AND (:to IS NULL OR f.occurredAt <= :to)
      ORDER BY f.occurredAt ASC
      """)
  List<Instant> findOccurredAtInRange(@Param("from") Instant from, @Param("to") Instant to);
}

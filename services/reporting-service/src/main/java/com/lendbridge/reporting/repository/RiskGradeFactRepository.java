package com.lendbridge.reporting.repository;

import com.lendbridge.reporting.model.RiskGradeFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskGradeFactRepository extends JpaRepository<RiskGradeFact, UUID> {

  /** Grade distribution in range — the stand-in for "defaults by grade" (see ADR 0022). */
  @Query(
      """
      SELECT f.grade, COUNT(f)
      FROM RiskGradeFact f
      WHERE (:from IS NULL OR f.occurredAt >= :from) AND (:to IS NULL OR f.occurredAt <= :to)
      GROUP BY f.grade
      ORDER BY f.grade ASC
      """)
  List<Object[]> countByGrade(@Param("from") Instant from, @Param("to") Instant to);
}

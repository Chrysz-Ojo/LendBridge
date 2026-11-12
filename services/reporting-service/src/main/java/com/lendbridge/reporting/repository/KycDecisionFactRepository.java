package com.lendbridge.reporting.repository;

import com.lendbridge.reporting.model.KycDecisionFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KycDecisionFactRepository extends JpaRepository<KycDecisionFact, UUID> {

  /** Verified vs rejected counts in range — backs the KYC-approval-rate report. */
  @Query(
      """
      SELECT f.decision, COUNT(f)
      FROM KycDecisionFact f
      WHERE (:from IS NULL OR f.occurredAt >= :from) AND (:to IS NULL OR f.occurredAt <= :to)
      GROUP BY f.decision
      ORDER BY f.decision ASC
      """)
  List<Object[]> countByDecision(@Param("from") Instant from, @Param("to") Instant to);
}

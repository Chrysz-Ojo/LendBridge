package com.lendbridge.reporting.repository;

import com.lendbridge.reporting.model.LoanPipelineFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanPipelineFactRepository extends JpaRepository<LoanPipelineFact, UUID> {

  /** Event counts and total amount per pipeline stage in range — backs the loan-pipeline-funnel
   * report. Counts events, not distinct loans: a loan that moved Listed then Delisted contributes
   * one row to each stage, which is the funnel's point (how many times each stage was reached). */
  @Query(
      """
      SELECT f.status, COUNT(f), SUM(f.amountKobo)
      FROM LoanPipelineFact f
      WHERE (:from IS NULL OR f.occurredAt >= :from) AND (:to IS NULL OR f.occurredAt <= :to)
      GROUP BY f.status
      ORDER BY f.status ASC
      """)
  List<Object[]> countByStatus(@Param("from") Instant from, @Param("to") Instant to);
}

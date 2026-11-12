package com.lendbridge.reporting.repository;

import com.lendbridge.reporting.model.InvestmentActivityFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestmentActivityFactRepository extends JpaRepository<InvestmentActivityFact, UUID> {

  /** Committed vs cancelled counts and total amount in range — backs the investment-activity
   * report. */
  @Query(
      """
      SELECT f.activityType, COUNT(f), SUM(f.amountKobo)
      FROM InvestmentActivityFact f
      WHERE (:from IS NULL OR f.occurredAt >= :from) AND (:to IS NULL OR f.occurredAt <= :to)
      GROUP BY f.activityType
      ORDER BY f.activityType ASC
      """)
  List<Object[]> countByActivityType(@Param("from") Instant from, @Param("to") Instant to);
}

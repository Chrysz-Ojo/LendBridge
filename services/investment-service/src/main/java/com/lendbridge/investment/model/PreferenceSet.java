package com.lendbridge.investment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A lender may save several of these (PRD E3-S1) — no uniqueness constraint on
 * {@code lenderUserId} beyond the primary key. Risk-grade filtering is deliberately absent: no
 * risk-service exists yet to grade loans against (docs/adr/0017). */
@Entity
@Table(name = "preference_set")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenceSet {

  @Id private UUID id;

  @Column(name = "lender_user_id", nullable = false)
  private UUID lenderUserId;

  @Column(name = "amount_per_loan_kobo", nullable = false)
  private long amountPerLoanKobo;

  @Column(name = "total_budget_kobo", nullable = false)
  private long totalBudgetKobo;

  @Column(name = "min_rate_bps", nullable = false)
  private int minRateBps;

  @Column(name = "min_tenor_months", nullable = false)
  private int minTenorMonths;

  @Column(name = "max_tenor_months", nullable = false)
  private int maxTenorMonths;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

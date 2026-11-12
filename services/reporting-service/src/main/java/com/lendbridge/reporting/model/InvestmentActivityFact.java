package com.lendbridge.reporting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per {@code investment.committed}/{@code investment.cancelled} event — backs the
 * investment-activity report. {@code lenderUserId} is nullable: {@code investment.cancelled}
 * doesn't carry it (confirmed on the copied Avro schema). */
@Entity
@Table(name = "investment_activity_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentActivityFact {

  @Id private UUID id;

  @Column(name = "commitment_id", nullable = false)
  private UUID commitmentId;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "lender_user_id")
  private UUID lenderUserId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Enumerated(EnumType.STRING)
  @Column(name = "activity_type", nullable = false)
  private InvestmentActivityType activityType;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}

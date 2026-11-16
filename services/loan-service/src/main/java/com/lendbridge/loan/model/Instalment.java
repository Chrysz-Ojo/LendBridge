package com.lendbridge.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per {@link RepaymentSchedule} instalment. {@code status} stays {@code PENDING} for
 * every instalment this build creates — flipping to {@code PAID} is wallet-funded repayment
 * processing, separate, not-yet-built scope. */
@Entity
@Table(name = "instalment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instalment {

  @Id private UUID id;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "instalment_number", nullable = false)
  private int instalmentNumber;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "principal_kobo", nullable = false)
  private long principalKobo;

  @Column(name = "interest_kobo", nullable = false)
  private long interestKobo;

  @Column(name = "total_kobo", nullable = false)
  private long totalKobo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InstalmentStatus status;

  @Column(name = "paid_at")
  private Instant paidAt;
}

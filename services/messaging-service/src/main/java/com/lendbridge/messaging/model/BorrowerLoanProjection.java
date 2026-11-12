package com.lendbridge.messaging.model;

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

/**
 * Private read-model projection built from {@code loan.listed} — marketplace-service's own
 * {@code ListingResponse} deliberately omits {@code borrowerUserId} (ADR 0016), so this service
 * gets it the same way every other service gets cross-service data: consuming the event, not
 * calling another service synchronously (no service-to-service auth exists in this repo yet).
 */
@Entity
@Table(name = "borrower_loan_projection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowerLoanProjection {

  @Id
  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

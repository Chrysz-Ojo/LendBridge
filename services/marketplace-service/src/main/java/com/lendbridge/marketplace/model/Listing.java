package com.lendbridge.marketplace.model;

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

/**
 * A read-model projection of loan-service's {@code LoanApplication}, built entirely from
 * {@code loan.listed}/{@code loan.delisted} events — never a cross-service foreign key or join
 * ("each service owns its database; never read another service's tables"). {@code id} is the
 * originating loan's own id, reused verbatim as this table's primary key, which is what makes the
 * event consumers' upsert idempotent on replay. {@code purpose} is a plain string, not a shared
 * enum with loan-service, so an unrecognized future value never breaks this consumer (events are
 * data, not a compile-time contract).
 */
@Entity
@Table(name = "listing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

  @Id private UUID id;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "interest_rate_bps", nullable = false)
  private int interestRateBps;

  @Column(name = "tenor_months", nullable = false)
  private int tenorMonths;

  @Column(name = "purpose", nullable = false)
  private String purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ListingStatus status;

  @Column(name = "listed_at", nullable = false)
  private Instant listedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

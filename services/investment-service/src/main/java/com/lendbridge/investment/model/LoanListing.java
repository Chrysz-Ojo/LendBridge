package com.lendbridge.investment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * This service's own read-model projection of loan-service's listed loans, built entirely from
 * {@code loan.listed}/{@code loan.delisted} — the same technique marketplace-service uses for its
 * own {@code Listing} ("each service owns its database; never read another service's tables").
 * This is deliberately a second, independent copy of the same source events rather than a
 * dependency on marketplace-service: it's what lets commitment validation (remaining-unfunded
 * amount, borrower-exposure cap) run entirely off this service's own data, with no synchronous
 * call to loan-service or marketplace-service (docs/adr/0017).
 */
@Entity
@Table(name = "loan_listing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanListing {

  @Id private UUID id;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(nullable = false)
  private boolean active;
}

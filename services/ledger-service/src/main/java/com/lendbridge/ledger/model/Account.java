package com.lendbridge.ledger.model;

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
 * A node in the chart of accounts — opened explicitly via the API, never implicitly created by a
 * posting. {@code ownerReference} is an opaque string (e.g. a user id or loan id from another
 * service), never a cross-service foreign key ("each service owns its database; never read
 * another service's tables"). {@code normalBalance} is fixed at creation time by the caller (who
 * knows whether this account behaves like an asset/wallet account or a revenue/fee account) —
 * this service never infers it from postings.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

  @Id private UUID id;

  @Column(name = "account_type", nullable = false)
  private String accountType;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "normal_balance", nullable = false)
  private DebitCredit normalBalance;

  @Column(name = "owner_reference")
  private String ownerReference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}

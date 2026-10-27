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
 * One leg of a {@link JournalEntry}. {@code direction} plus an unsigned {@code amountKobo} —
 * never a single signed column — because that is how double-entry books are actually read and
 * audited; a signed column would force every reader to re-derive sign meaning from the account's
 * own normal-balance convention.
 */
@Entity
@Table(name = "posting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Posting {

  @Id private UUID id;

  @Column(name = "journal_entry_id", nullable = false)
  private UUID journalEntryId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false)
  private DebitCredit direction;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}

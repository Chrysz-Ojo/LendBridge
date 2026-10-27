package com.lendbridge.ledger.model;

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
 * Append-only — no update/delete path exists anywhere in this service. {@code idempotencyKey} is
 * unique and is how a retried {@code POST /journal-entries} call is detected and replayed instead
 * of double-posted. A journal entry's postings are a separate {@link Posting} row per leg, joined
 * only by {@code journalEntryId} (no JPA relationship mapping, matching this repo's flat-entity
 * convention) — always assembled in the service layer, never lazy-loaded.
 */
@Entity
@Table(name = "journal_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

  @Id private UUID id;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "reference")
  private String reference;

  @Column(name = "description")
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}

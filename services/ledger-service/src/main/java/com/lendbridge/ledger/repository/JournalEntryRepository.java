package com.lendbridge.ledger.repository;

import com.lendbridge.ledger.model.JournalEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

  Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);
}

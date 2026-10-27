package com.lendbridge.ledger.repository;

import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.model.Posting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

  List<Posting> findAllByJournalEntryIdOrderByIdAsc(UUID journalEntryId);

  List<Posting> findAllByAccountIdOrderByIdAsc(UUID accountId, Pageable pageable);

  List<Posting> findAllByAccountIdAndIdGreaterThanOrderByIdAsc(UUID accountId, UUID after, Pageable pageable);

  /** {@code id > :after} relies on UUIDv7's byte order matching creation order — the same
   * property that makes UUIDv7 "time-ordered" in the first place (CLAUDE.md / PRD). */
  @Query(
      "select coalesce(sum(p.amountKobo), 0) from Posting p "
          + "where p.accountId = :accountId and p.direction = :direction")
  long sumAmountByAccountIdAndDirection(
      @Param("accountId") UUID accountId, @Param("direction") DebitCredit direction);
}

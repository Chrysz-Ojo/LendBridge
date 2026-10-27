package com.lendbridge.ledger.outbox;

import com.lendbridge.ledger.model.JournalEntry;
import com.lendbridge.ledger.model.Posting;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JSON shape stored in the outbox row; mirrors the fields of the ledger_posted.avsc schema. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerPostedPayload {
  private String journalEntryId;
  private String idempotencyKey;
  private String currency;
  private String reference;
  private String correlationId;
  private long postedAtEpochMillis;
  private List<PostingPayload> postings;

  public static LedgerPostedPayload from(JournalEntry entry, List<Posting> postings, String correlationId) {
    List<PostingPayload> postingPayloads =
        postings.stream()
            .map(
                p ->
                    new PostingPayload(
                        p.getId().toString(),
                        p.getAccountId().toString(),
                        p.getDirection().name(),
                        p.getAmountKobo()))
            .toList();
    return new LedgerPostedPayload(
        entry.getId().toString(),
        entry.getIdempotencyKey(),
        entry.getCurrency(),
        entry.getReference(),
        correlationId,
        entry.getCreatedAt().toEpochMilli(),
        postingPayloads);
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PostingPayload {
    private String postingId;
    private String accountId;
    private String direction;
    private long amountKobo;
  }
}

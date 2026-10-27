package com.lendbridge.ledger.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntryResponse {
  private UUID id;
  private String idempotencyKey;
  private String currency;
  private String reference;
  private String description;
  private Instant createdAt;
  private List<PostingResponse> postings;
}

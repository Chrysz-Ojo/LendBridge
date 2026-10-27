package com.lendbridge.ledger.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code nextCursor} is the last returned posting's id (UUIDv7, time-ordered) — pass it back as
 * {@code after} to fetch the next page; {@code null} means there is no more data. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingPageResponse {
  private List<PostingResponse> postings;
  private UUID nextCursor;
}

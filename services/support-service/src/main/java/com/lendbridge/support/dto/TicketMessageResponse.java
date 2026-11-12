package com.lendbridge.support.dto;

import com.lendbridge.support.model.AuthorRole;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageResponse {
  private UUID id;
  private UUID ticketId;
  private UUID authorUserId;
  private AuthorRole authorRole;
  private String body;
  private Instant createdAt;
}

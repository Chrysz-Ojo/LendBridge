package com.lendbridge.support.outbox;

import com.lendbridge.support.model.AuthorRole;
import com.lendbridge.support.model.TicketStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Deliberately excludes the message body — same metadata-only privacy precedent as
 * messaging-service's {@code message.sent} (a downstream consumer can only render a generic
 * "your ticket was updated" notice, never see ticket content). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketUpdatedPayload {
  private UUID ticketId;
  private UUID userId;
  private TicketStatus status;
  private AuthorRole lastMessageAuthorRole;
  private long occurredAtEpochMillis;
}

package com.lendbridge.messaging.service;

import com.lendbridge.messaging.dto.MessageResponse;
import com.lendbridge.messaging.dto.SendMessageRequest;
import java.util.List;
import java.util.UUID;

public interface MessageService {

  /** Validates {@code senderUserId} is a participant, masks the body, persists, writes the
   * outbox event, and publishes to the realtime fan-out channel — one transactional path used by
   * both the STOMP send handler and (if ever needed) a REST fallback. */
  MessageResponse send(SendMessageRequest request, UUID senderUserId);

  /** Cursor page of history: no cursor (afterId = the all-zero UUID sentinel) returns the latest
   * page; a real afterId doubles as the polling-fallback call ("anything newer than this"). */
  List<MessageResponse> listSince(UUID conversationId, UUID afterId, int limit, UUID callerUserId);
}

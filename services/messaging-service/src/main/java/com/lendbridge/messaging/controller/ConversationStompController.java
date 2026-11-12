package com.lendbridge.messaging.controller;

import com.lendbridge.messaging.dto.SendMessageRequest;
import com.lendbridge.messaging.security.StompPrincipal;
import com.lendbridge.messaging.service.MessageService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/** The live-push send path: client publishes to {@code /app/conversations/{conversationId}/messages};
 * the actual broadcast to {@code /topic/conversations/{conversationId}} happens via the Redis
 * fan-out bridge in {@code realtime}, not directly from here — see {@code MessageServiceImpl}. */
@Controller
@RequiredArgsConstructor
public class ConversationStompController {

  private final MessageService messageService;

  @MessageMapping("/conversations/{conversationId}/messages")
  public void send(
      @DestinationVariable UUID conversationId, @Payload SendMessageRequest request, Principal principal) {
    UUID senderUserId = ((StompPrincipal) principal).getUserId();
    messageService.send(
        SendMessageRequest.builder().conversationId(conversationId).body(request.getBody()).build(), senderUserId);
  }
}

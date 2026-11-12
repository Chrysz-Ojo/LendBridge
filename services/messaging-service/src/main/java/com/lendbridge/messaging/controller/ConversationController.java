package com.lendbridge.messaging.controller;

import com.lendbridge.messaging.dto.ConversationResponse;
import com.lendbridge.messaging.dto.MessageResponse;
import com.lendbridge.messaging.security.CurrentUser;
import com.lendbridge.messaging.service.ConversationService;
import com.lendbridge.messaging.service.MessageService;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET .../messages} doubles as both the initial-history endpoint (no {@code afterId})
 * and the explicit polling fallback for new messages ({@code afterId} = the caller's last-seen
 * message id) — one endpoint, same UUIDv7-ordered cursor either way. */
@RestController
@RequestMapping("/api/v1/messaging/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationService conversationService;
  private final MessageService messageService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ConversationResponse>>> findMine(Authentication authentication) {
    UUID callerUserId = CurrentUser.idFrom(authentication);
    return ResponseEntity.ok(ApiResponse.success(conversationService.findMine(callerUserId)));
  }

  @GetMapping("/{conversationId}/messages")
  public ResponseEntity<ApiResponse<List<MessageResponse>>> listMessages(
      @PathVariable UUID conversationId,
      @RequestParam(required = false) UUID afterId,
      @RequestParam(defaultValue = "50") int limit,
      Authentication authentication) {
    UUID callerUserId = CurrentUser.idFrom(authentication);
    return ResponseEntity.ok(
        ApiResponse.success(messageService.listSince(conversationId, afterId, limit, callerUserId)));
  }
}

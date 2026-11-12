package com.lendbridge.messaging.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Runs identically on every pod — pushes to whichever STOMP sessions are connected locally,
 * regardless of which pod actually received the original client send. */
@Component
@RequiredArgsConstructor
public class ConversationEventSubscriber implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(ConversationEventSubscriber.class);

  private final SimpMessagingTemplate simpMessagingTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      MessagePushPayload payload = objectMapper.readValue(message.getBody(), MessagePushPayload.class);
      simpMessagingTemplate.convertAndSend(
          "/topic/conversations/" + payload.getMessage().getConversationId(), payload.getMessage());
    } catch (Exception e) {
      log.error("Failed to relay conversation event to local STOMP sessions", e);
    }
  }
}

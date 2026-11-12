package com.lendbridge.messaging.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.messaging.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConversationEventPublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public void publish(MessageResponse message) {
    try {
      String json = objectMapper.writeValueAsString(MessagePushPayload.builder().message(message).build());
      redisTemplate.convertAndSend(RedisConfig.CONVERSATION_EVENTS_CHANNEL, json);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to publish conversation event for message " + message.getId(), e);
    }
  }
}

package com.lendbridge.messaging.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Wires the Redis pub/sub bridge used for cross-pod STOMP fan-out (PRD: "Redis pub/sub across
 * pods"). This is a home-grown publish/subscribe bridge, not a full external STOMP broker relay
 * (e.g. RabbitMQ) — see ADR 0019 for why. Every pod runs an identical subscriber; whichever pod a
 * client is connected to re-publishes locally via {@code SimpMessagingTemplate} when a message
 * event arrives on the shared channel, regardless of which pod actually received the client send.
 */
@Configuration
public class RedisConfig {

  public static final String CONVERSATION_EVENTS_CHANNEL = "messaging:conversation-events";

  @Bean
  public ChannelTopic conversationEventsTopic() {
    return new ChannelTopic(CONVERSATION_EVENTS_CHANNEL);
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      ConversationEventSubscriber subscriber,
      ChannelTopic conversationEventsTopic) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, conversationEventsTopic);
    return container;
  }
}

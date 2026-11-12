package com.lendbridge.messaging.realtime;

import com.lendbridge.messaging.dto.MessageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Serialized onto the Redis pub/sub channel so every pod — including the one that received the
 * original send — pushes the message out to its own locally-connected STOMP sessions. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePushPayload {
  private MessageResponse message;
}

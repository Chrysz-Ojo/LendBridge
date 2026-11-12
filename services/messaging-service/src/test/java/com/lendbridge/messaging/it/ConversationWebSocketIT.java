package com.lendbridge.messaging.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.lendbridge.messaging.dto.MessageResponse;
import com.lendbridge.messaging.dto.SendMessageRequest;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.model.ConversationStatus;
import com.lendbridge.messaging.support.AbstractIntegrationTest;
import com.lendbridge.starter.crypto.UuidV7;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Proves the live-push round trip (CONNECT with a JWT -> SEND -> mask/persist/outbox/Redis-publish
 * -> subscriber re-broadcast -> client receives) within a single JVM. True cross-pod fan-out is a
 * manual check (see the plan's Verification section) — this single-instance test still exercises
 * the real publish/subscribe loop, just within one pod.
 *
 * <p>{@link JwtDecoder} is mocked rather than hitting a real JWKS endpoint: no authentication-service
 * instance runs in this per-service IT environment (same reasoning MockMvc's {@code jwt()} test
 * support sidesteps for the REST ITs), so the decoder is stubbed to accept the test token and
 * return a fixed subject — everything downstream of that (the interceptor, the STOMP handler, the
 * masking/persist/outbox/Redis path) is exercised for real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConversationWebSocketIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @MockBean private JwtDecoder jwtDecoder;

  private UUID borrowerUserId;
  private UUID lenderUserId;
  private Conversation conversation;

  @BeforeEach
  void seedConversation() {
    borrowerUserId = UUID.randomUUID();
    lenderUserId = UUID.randomUUID();
    Instant now = Instant.now();
    conversation =
        conversationRepository.save(
            Conversation.builder()
                .id(UuidV7.generate())
                .loanId(UUID.randomUUID())
                .borrowerUserId(borrowerUserId)
                .lenderUserId(lenderUserId)
                .status(ConversationStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build());
  }

  @Test
  void sendOverStomp_broadcastsMaskedMessageToSubscribers() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("sub", borrowerUserId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);

    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer any-token-the-mocked-decoder-accepts");

    CompletableFuture<StompSession> sessionFuture =
        stompClient.connectAsync(
            "ws://localhost:" + port + "/ws",
            (org.springframework.web.socket.WebSocketHttpHeaders) null,
            connectHeaders,
            new StompSessionHandlerAdapter() {});
    StompSession session = sessionFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);

    CompletableFuture<MessageResponse> receivedFuture = new CompletableFuture<>();
    session.subscribe(
        "/topic/conversations/" + conversation.getId(),
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return MessageResponse.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            receivedFuture.complete((MessageResponse) payload);
          }
        });

    // give the broker a moment to register the subscription before sending
    Thread.sleep(500);

    session.send(
        "/app/conversations/" + conversation.getId() + "/messages",
        SendMessageRequest.builder().body("call 08012345678").build());

    MessageResponse received = receivedFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
    assertThat(received.getBody()).isEqualTo("call [redacted]");
    assertThat(received.getConversationId()).isEqualTo(conversation.getId());
    assertThat(received.getSenderUserId()).isEqualTo(borrowerUserId);

    assertThat(messageRepository.findAll().stream().filter(m -> m.getConversationId().equals(conversation.getId())))
        .hasSize(1);

    session.disconnect();
  }
}

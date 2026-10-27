package com.lendbridge.payment.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stand-in for authentication-service's {@code POST /api/v1/auth/mfa/totp/step-up-verify}. Valid
 * by default so most withdrawal tests don't need to think about 2FA at all; call
 * {@link #setNextVerificationResult(boolean)} for tests specifically exercising a rejected code.
 */
public final class AuthServiceStub {

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicBoolean nextVerificationResult = new AtomicBoolean(true);

  private AuthServiceStub(HttpServer server) {
    this.server = server;
  }

  public static AuthServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      AuthServiceStub stub = new AuthServiceStub(server);
      server.createContext("/api/v1/auth/mfa/totp/step-up-verify", stub::handle);
      server.setExecutor(null);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start AuthServiceStub", e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public void setNextVerificationResult(boolean valid) {
    nextVerificationResult.set(valid);
  }

  public void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    if (nextVerificationResult.get()) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("message", "Step-up verification succeeded");
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("success", true);
      envelope.put("data", data);
      envelope.put("message", null);
      envelope.put("timestamp", java.time.Instant.now().toString());
      byte[] bytes = objectMapper.writeValueAsBytes(envelope);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    } else {
      exchange.sendResponseHeaders(401, -1);
    }
  }
}

package com.lendbridge.investment.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** A minimal stand-in for wallet-service's {@code POST /api/v1/wallets/me/holds} and
 * {@code DELETE /api/v1/wallets/me/holds/{id}}, built on the JDK's own
 * {@code com.sun.net.httpserver.HttpServer} — same pattern as loan-service's own
 * {@code KycServiceStub}/{@code DocumentServiceStub}: this service never depends on
 * wallet-service's database, only its REST API. */
public final class WalletServiceStub {

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicInteger holdsCreated = new AtomicInteger();
  private final AtomicInteger holdsReleased = new AtomicInteger();
  private final CopyOnWriteArrayList<String> authorizationHeadersSeen = new CopyOnWriteArrayList<>();

  private WalletServiceStub(HttpServer server) {
    this.server = server;
  }

  public static WalletServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      WalletServiceStub stub = new WalletServiceStub(server);
      server.createContext("/api/v1/wallets/me/holds", stub::handle);
      server.setExecutor(null);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start WalletServiceStub", e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public int holdsCreated() {
    return holdsCreated.get();
  }

  public int holdsReleased() {
    return holdsReleased.get();
  }

  public java.util.List<String> authorizationHeadersSeen() {
    return authorizationHeadersSeen;
  }

  public void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    authorizationHeadersSeen.add(exchange.getRequestHeaders().getFirst("Authorization"));
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    boolean isHoldSubPath = !path.equals("/api/v1/wallets/me/holds");

    if ("POST".equals(method) && !isHoldSubPath) {
      holdsCreated.incrementAndGet();
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("id", UUID.randomUUID().toString());
      respond(exchange, 201, envelope(data));
      return;
    }
    if ("DELETE".equals(method) && isHoldSubPath) {
      holdsReleased.incrementAndGet();
      respond(exchange, 200, envelope(null));
      return;
    }
    exchange.sendResponseHeaders(404, -1);
  }

  private Map<String, Object> envelope(Object data) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("success", true);
    envelope.put("data", data);
    envelope.put("message", null);
    return envelope;
  }

  private void respond(HttpExchange exchange, int status, Object body) throws IOException {
    byte[] bytes = objectMapper.writeValueAsBytes(body);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}

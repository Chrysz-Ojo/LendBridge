package com.lendbridge.loan.support;

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

/**
 * A minimal stand-in for document-service's {@code POST /api/v1/documents} multipart upload,
 * built on the JDK's own {@code com.sun.net.httpserver.HttpServer} — same pattern as kyc-service's
 * own {@code DocumentServiceStub}. Doesn't parse the multipart body (not needed to verify
 * loan-service's own behaviour); just drains it and records the request count and the
 * {@code Authorization} header each upload carried, so tests can assert the borrower's own JWT
 * (not a risk officer's) was forwarded.
 */
public final class DocumentServiceStub {

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicInteger uploadCount = new AtomicInteger();
  private final CopyOnWriteArrayList<String> authorizationHeadersSeen = new CopyOnWriteArrayList<>();

  private DocumentServiceStub(HttpServer server) {
    this.server = server;
  }

  public static DocumentServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      DocumentServiceStub stub = new DocumentServiceStub(server);
      server.createContext("/api/v1/documents", stub::handle);
      server.setExecutor(null);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start DocumentServiceStub", e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public int uploadCount() {
    return uploadCount.get();
  }

  public java.util.List<String> authorizationHeadersSeen() {
    return authorizationHeadersSeen;
  }

  public void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    exchange.getRequestBody().readAllBytes();
    uploadCount.incrementAndGet();
    authorizationHeadersSeen.add(exchange.getRequestHeaders().getFirst("Authorization"));

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", UUID.randomUUID().toString());
    respond(exchange, 200, envelope(data));
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

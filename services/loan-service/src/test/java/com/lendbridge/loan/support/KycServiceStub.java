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

/**
 * A minimal stand-in for kyc-service's {@code GET /api/v1/kyc/verifications/me}, built on the
 * JDK's own {@code com.sun.net.httpserver.HttpServer} — same pattern as kyc-service's own
 * {@code DocumentServiceStub}: loan-service never depends on kyc-service's database or Kafka
 * topics, only its REST API. Defaults to a fully-verified {@code TIER_3} response so most tests
 * don't need setup; call {@link #setTrustTier(String)} or {@link #respondNotVerified()} to change
 * what the next requests see.
 */
public final class KycServiceStub {

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private volatile String trustTier = "TIER_3";
  private volatile boolean notVerified = false;

  private KycServiceStub(HttpServer server) {
    this.server = server;
  }

  public static KycServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      KycServiceStub stub = new KycServiceStub(server);
      server.createContext("/api/v1/kyc/verifications/me", stub::handle);
      server.setExecutor(null);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start KycServiceStub", e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public void setTrustTier(String trustTier) {
    this.notVerified = false;
    this.trustTier = trustTier;
  }

  public void respondNotVerified() {
    this.notVerified = true;
  }

  public void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    if (notVerified) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", UUID.randomUUID().toString());
    data.put("status", "VERIFIED");
    data.put("trustTier", trustTier);
    data.put("reviewRequired", false);
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

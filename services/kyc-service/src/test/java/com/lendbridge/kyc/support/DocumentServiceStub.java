package com.lendbridge.kyc.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal stand-in for document-service's {@code GET /api/v1/documents/{id}}, built on the JDK's
 * own {@code com.sun.net.httpserver.HttpServer} — same pattern as payment-service's
 * {@code WalletServiceStub}/{@code AuthServiceStub}, chosen for the same reason: kyc-service never
 * depends on document-service's database or Kafka topics, only its REST API.
 */
public final class DocumentServiceStub {

  private static final Pattern DOCUMENT_PATH = Pattern.compile("^/api/v1/documents/([^/]+)$");

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<UUID, Map<String, Object>> documents = new ConcurrentHashMap<>();

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

  /** Registers a document the stub will serve for {@code GET /api/v1/documents/{id}}. */
  public UUID registerDocument(String documentType, String scanStatus, String originalFilename) {
    UUID id = UUID.randomUUID();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", id.toString());
    data.put("documentType", documentType);
    data.put("originalFilename", originalFilename);
    data.put("contentType", "image/jpeg");
    data.put("sizeBytes", 1024);
    data.put("checksumSha256", "deadbeef");
    data.put("scanStatus", scanStatus);
    data.put("createdAt", Instant.now().toString());
    documents.put(id, data);
    return id;
  }

  public void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    Matcher matcher = DOCUMENT_PATH.matcher(path);
    if (!"GET".equals(exchange.getRequestMethod()) || !matcher.matches()) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    UUID id;
    try {
      id = UUID.fromString(matcher.group(1));
    } catch (IllegalArgumentException e) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    Map<String, Object> data = documents.get(id);
    if (data == null) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    respond(exchange, 200, envelope(data));
  }

  private Map<String, Object> envelope(Object data) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("success", true);
    envelope.put("data", data);
    envelope.put("message", null);
    envelope.put("timestamp", Instant.now().toString());
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

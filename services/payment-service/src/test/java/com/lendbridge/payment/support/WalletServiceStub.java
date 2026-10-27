package com.lendbridge.payment.support;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * A minimal stand-in for the three wallet-service endpoints payment-service calls, built on the
 * JDK's own {@code com.sun.net.httpserver.HttpServer} — same pattern as wallet-service's own
 * {@code LedgerServiceStub}, chosen for the same reason: payment-service never depends on
 * wallet-service's database or Kafka topics, only its REST API.
 */
public final class WalletServiceStub {

  private static final Pattern BANK_ACCOUNT_PATH =
      Pattern.compile("^/api/v1/wallets/me/bank-accounts/([^/]+)$");

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID walletId = UUID.randomUUID();
  private final UUID ledgerAccountId = UUID.randomUUID();
  private final AtomicLong availableKobo = new AtomicLong(1_000_000_00L);
  private volatile String bankVerificationStatus = "VERIFIED";
  private final CopyOnWriteArrayList<Map<String, Object>> registeredVirtualAccounts =
      new CopyOnWriteArrayList<>();

  private WalletServiceStub(HttpServer server) {
    this.server = server;
  }

  public static WalletServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      WalletServiceStub stub = new WalletServiceStub(server);
      server.createContext("/api/v1/wallets/me", stub::handle);
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

  public UUID walletId() {
    return walletId;
  }

  public UUID ledgerAccountId() {
    return ledgerAccountId;
  }

  public void setAvailableKobo(long value) {
    availableKobo.set(value);
  }

  public void setBankVerificationStatus(String status) {
    this.bankVerificationStatus = status;
  }

  public java.util.List<Map<String, Object>> registeredVirtualAccounts() {
    return registeredVirtualAccounts;
  }

  public void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    var bankAccountMatcher = BANK_ACCOUNT_PATH.matcher(path);

    if ("GET".equals(method) && "/api/v1/wallets/me".equals(path)) {
      handleWalletSummary(exchange);
    } else if ("GET".equals(method) && bankAccountMatcher.matches()) {
      handleBankAccountForPayout(exchange, bankAccountMatcher.group(1));
    } else if ("POST".equals(method) && "/api/v1/wallets/me/virtual-accounts".equals(path)) {
      handleRegisterVirtualAccount(exchange);
    } else {
      exchange.sendResponseHeaders(404, -1);
    }
  }

  private void handleWalletSummary(HttpExchange exchange) throws IOException {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", walletId.toString());
    data.put("ledgerAccountId", ledgerAccountId.toString());
    data.put("currency", "NGN");
    data.put("ledgerBalanceKobo", availableKobo.get());
    data.put("heldKobo", 0);
    data.put("availableKobo", availableKobo.get());
    data.put("stale", false);
    respond(exchange, 200, envelope(data));
  }

  private void handleBankAccountForPayout(HttpExchange exchange, String id) throws IOException {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", id);
    data.put("bankCode", "058");
    data.put("bankName", "GTBank");
    data.put("accountNumber", "0123456789");
    data.put("accountHolderName", "Jane Doe");
    data.put("verificationStatus", bankVerificationStatus);
    respond(exchange, 200, envelope(data));
  }

  private void handleRegisterVirtualAccount(HttpExchange exchange) throws IOException {
    byte[] requestBody = exchange.getRequestBody().readAllBytes();
    @SuppressWarnings("unchecked")
    Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
    registeredVirtualAccounts.add(request);
    respond(exchange, 201, envelope(Map.of()));
  }

  private Map<String, Object> envelope(Object data) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("success", true);
    envelope.put("data", data);
    envelope.put("message", null);
    envelope.put("timestamp", java.time.Instant.now().toString());
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

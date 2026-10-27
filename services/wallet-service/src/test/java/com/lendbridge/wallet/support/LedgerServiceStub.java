package com.lendbridge.wallet.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * A minimal stand-in for ledger-service's {@code POST /accounts} and {@code GET
 * /accounts/{id}/balance}, built on the JDK's own {@code com.sun.net.httpserver.HttpServer} (zero
 * extra dependency) rather than reactor-netty (api-gateway's {@code AuthServiceStub} uses that, but
 * only because api-gateway is already a WebFlux app; wallet-service is plain blocking Spring MVC
 * and doesn't have reactor-netty on its classpath). Every opened account defaults to a generous
 * balance so most tests don't need to think about it; call {@link #setBalance(UUID, long)} for
 * tests that specifically exercise insufficient-funds behavior.
 */
public final class LedgerServiceStub {

  private static final long DEFAULT_BALANCE_KOBO = 1_000_000_00L;
  private static final Pattern BALANCE_PATH =
      Pattern.compile("^/api/v1/ledger/accounts/([^/]+)/balance$");

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, Long> balances = new ConcurrentHashMap<>();

  private LedgerServiceStub(HttpServer server) {
    this.server = server;
  }

  public static LedgerServiceStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      LedgerServiceStub stub = new LedgerServiceStub(server);
      server.createContext("/api/v1/ledger/accounts", stub::handle);
      server.setExecutor(null);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start LedgerServiceStub", e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public void setBalance(UUID accountId, long balanceKobo) {
    balances.put(accountId.toString(), balanceKobo);
  }

  public void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    var balanceMatcher = BALANCE_PATH.matcher(path);
    if ("POST".equals(method) && "/api/v1/ledger/accounts".equals(path)) {
      handleOpenAccount(exchange);
    } else if ("GET".equals(method) && balanceMatcher.matches()) {
      handleGetBalance(exchange, balanceMatcher.group(1));
    } else {
      exchange.sendResponseHeaders(404, -1);
    }
  }

  private void handleOpenAccount(HttpExchange exchange) throws IOException {
    String accountId = UUID.randomUUID().toString();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", accountId);
    data.put("accountType", "WALLET");
    data.put("currency", "NGN");
    data.put("normalBalance", "CREDIT");
    writeJson(exchange, 201, envelope(data));
  }

  private void handleGetBalance(HttpExchange exchange, String accountId) throws IOException {
    long balance = balances.getOrDefault(accountId, DEFAULT_BALANCE_KOBO);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("accountId", accountId);
    data.put("currency", "NGN");
    data.put("balanceKobo", balance);
    writeJson(exchange, 200, envelope(data));
  }

  private Map<String, Object> envelope(Map<String, Object> data) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("success", true);
    envelope.put("data", data);
    return envelope;
  }

  private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
    byte[] bytes = objectMapper.writeValueAsBytes(body);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}

package com.lendbridge.payment.support;

import com.lendbridge.payment.repository.PaymentIntentRepository;
import com.lendbridge.payment.repository.PayoutRepository;
import com.lendbridge.payment.repository.ProviderTransactionRepository;
import com.lendbridge.payment.repository.WebhookLogRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres + Kafka + Schema Registry Testcontainers, started manually in a {@code static} block
 * (not {@code @Testcontainers}/{@code @Container}) for the same reason as every prior service's
 * IT fixture this session — one singleton set of containers alive for the whole suite. No Redis
 * (payment-service has no cache-fallback read path). {@code payment.provider.primary/secondary}
 * is always {@code fake} here — the real adapters are exercised only by their own narrow unit
 * tests, never by this IT or CI (see the approved plan's Tests section).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  private static final Network NETWORK = Network.newNetwork();
  private static final String FIELD_ENCRYPTION_KEY = randomBase64Key();

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("payment_db")
          .withUsername("payment_service")
          .withPassword("test-password");

  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))
          .withNetwork(NETWORK)
          .withNetworkAliases("kafka");

  static final GenericContainer<?> SCHEMA_REGISTRY =
      new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.7.1"))
          .withNetwork(NETWORK)
          .withNetworkAliases("schema-registry")
          .withExposedPorts(8081)
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
          .dependsOn(KAFKA);

  protected static final WalletServiceStub WALLET_STUB = WalletServiceStub.start();
  protected static final AuthServiceStub AUTH_STUB = AuthServiceStub.start();

  static {
    POSTGRES.start();
    KAFKA.start();
    SCHEMA_REGISTRY.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add(
        "spring.kafka.consumer.properties.schema.registry.url",
        () -> "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));
    registry.add(
        "spring.kafka.producer.properties.schema.registry.url",
        () -> "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));

    registry.add("platform.field-encryption.key", () -> FIELD_ENCRYPTION_KEY);
    registry.add("platform.outbox.relay-interval-ms", () -> "500");
    registry.add("platform.outbox.ce-source", () -> "lendbridge://payment-service");

    registry.add("payment.provider.primary", () -> "fake");
    registry.add("payment.provider.secondary", () -> "fake");

    registry.add("WALLET_SERVICE_URL", WALLET_STUB::baseUrl);
    registry.add("AUTH_SERVICE_URL", AUTH_STUB::baseUrl);
  }

  private static String randomBase64Key() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected PaymentIntentRepository paymentIntentRepository;
  @Autowired protected PayoutRepository payoutRepository;
  @Autowired protected ProviderTransactionRepository providerTransactionRepository;
  @Autowired protected WebhookLogRepository webhookLogRepository;
}

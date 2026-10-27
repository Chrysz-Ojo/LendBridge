package com.lendbridge.document.support;

import java.security.SecureRandom;
import java.util.Base64;

import com.lendbridge.document.repository.DocumentRepository;
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
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers fixture: Postgres + Kafka + Schema Registry + LocalStack (S3), all
 * started manually in a {@code static} block rather than via {@code
 * @Testcontainers}/{@code @Container}, which would stop them in {@code afterAll()} of whichever
 * IT class runs first even though the fields are static — manual start keeps one singleton set of
 * containers alive for the whole suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  private static final Network NETWORK = Network.newNetwork();
  private static final String BUCKET = "test-documents";
  private static final String FIELD_ENCRYPTION_KEY = randomBase64Key();

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("document_db")
          .withUsername("document_service")
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

  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.0"))
          .withServices(Service.S3);

  static {
    POSTGRES.start();
    KAFKA.start();
    SCHEMA_REGISTRY.start();
    LOCALSTACK.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add(
        "spring.kafka.producer.properties.schema.registry.url",
        () -> "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));

    registry.add("platform.field-encryption.key", () -> FIELD_ENCRYPTION_KEY);
    registry.add("platform.outbox.relay-interval-ms", () -> "500");
    registry.add("platform.outbox.ce-source", () -> "lendbridge://document-service");

    registry.add(
        "documents.storage.endpoint", () -> LOCALSTACK.getEndpointOverride(Service.S3).toString());
    registry.add("documents.storage.region", LOCALSTACK::getRegion);
    registry.add("documents.storage.bucket", () -> BUCKET);
    registry.add("documents.storage.access-key", LOCALSTACK::getAccessKey);
    registry.add("documents.storage.secret-key", LOCALSTACK::getSecretKey);
    registry.add("documents.storage.path-style-access", () -> "true");
  }

  private static String randomBase64Key() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected DocumentRepository documentRepository;
}

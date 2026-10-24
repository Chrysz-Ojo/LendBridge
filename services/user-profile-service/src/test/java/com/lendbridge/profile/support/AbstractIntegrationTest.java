package com.lendbridge.profile.support;

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

import com.lendbridge.profile.repository.UserProfileRepository;
import com.lendbridge.profile.repository.UserProfileRoleAssignmentRepository;
import com.lendbridge.profile.repository.ProcessedEventRepository;

/**
 * Shared Testcontainers fixture: Postgres + Kafka + a plain GenericContainer running Confluent's
 * Schema Registry on the same network (no official Confluent Testcontainers module exists).
 *
 * <p>These are started manually below rather than via {@code @Testcontainers}/{@code @Container},
 * which would stop them in {@code afterAll()} of whichever IT class happens to run first — even
 * though the fields are static. Manual start keeps one singleton set of containers alive for the
 * whole suite; Ryuk reaps them at JVM exit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  private static final Network NETWORK = Network.newNetwork();
  private static final String FIELD_ENCRYPTION_KEY = randomBase64Key();

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("user_profile_db")
          .withUsername("user_profile_service")
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
    registry.add("platform.outbox.ce-source", () -> "lendbridge://user-profile-service");
  }

  public static String schemaRegistryUrl() {
    return "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081);
  }

  public static String kafkaBootstrapServers() {
    return KAFKA.getBootstrapServers();
  }

  private static String randomBase64Key() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected UserProfileRepository userProfileRepository;
  @Autowired protected UserProfileRoleAssignmentRepository roleAssignmentRepository;
  @Autowired protected ProcessedEventRepository processedEventRepository;
}

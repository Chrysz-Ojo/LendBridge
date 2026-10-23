package com.lendbridge.authentication.support;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers fixture: Postgres + Kafka + a plain GenericContainer running Confluent's
 * Schema Registry on the same network (no official Confluent Testcontainers module exists).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  private static final Network NETWORK = Network.newNetwork();
  private static final String FIELD_ENCRYPTION_KEY = randomBase64Key();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("authentication_db")
          .withUsername("authentication_service")
          .withPassword("test-password");

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))
          .withNetwork(NETWORK)
          .withNetworkAliases("kafka");

  @Container
  static final GenericContainer<?> SCHEMA_REGISTRY =
      new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.7.1"))
          .withNetwork(NETWORK)
          .withNetworkAliases("schema-registry")
          .withExposedPorts(8081)
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
          .dependsOn(KAFKA);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add(
        "spring.kafka.producer.properties.schema.registry.url",
        () -> "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));

    registry.add("auth.field-encryption.key", () -> FIELD_ENCRYPTION_KEY);
    registry.add("auth.phone-hash.pepper", () -> "test-phone-hash-pepper");
    registry.add("auth.jwt.private-key-pem", () -> "");
    registry.add("auth.jwt.public-key-pem", () -> "");
    registry.add(
        "auth.registration.email-verification-base-url", () -> "http://localhost/verify-email");
    registry.add("auth.password-reset.base-url", () -> "http://localhost/reset-password");
    registry.add("auth.outbox.relay-interval-ms", () -> "500");
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

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.lendbridge.authentication.repository.UserJpaRepository userRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.lendbridge.authentication.crypto.HmacPhoneHasher hmacPhoneHasher;

  /** Creates a fully-ACTIVE user directly, bypassing the registration flow, for tests whose focus
   * is login/MFA/password-reset rather than registration itself. */
  protected com.lendbridge.authentication.model.User createActiveUser(
      String email, String phone, String rawPassword) {
    java.time.Instant now = java.time.Instant.now();
    com.lendbridge.authentication.model.User user =
        com.lendbridge.authentication.model.User.builder()
            .id(com.lendbridge.authentication.crypto.UuidV7.generate())
            .email(email)
            .phone(phone)
            .phoneHash(hmacPhoneHasher.hash(phone))
            .passwordHash(passwordEncoder.encode(rawPassword))
            .status(com.lendbridge.authentication.model.UserStatus.ACTIVE)
            .fullName("Test User")
            .emailVerified(true)
            .phoneVerified(true)
            .mfaEnabled(false)
            .failedLoginAttempts(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
    return userRepository.save(user);
  }
}

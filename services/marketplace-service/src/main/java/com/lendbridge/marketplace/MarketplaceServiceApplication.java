package com.lendbridge.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans (e.g. {@code GlobalExceptionHandler}, {@code FieldCipher}) get picked up.
 * {@code @EntityScan}/{@code @EnableJpaRepositories} are needed for the same reason: Spring Boot's
 * default entity/repository scanning is anchored to this class's own package.
 *
 * <p>Phase 1 publishes nothing of its own — just {@code @KafkaListener} consumers in
 * {@code messaging/} — so there is no {@code AbstractOutboxRelay} subclass here, unlike every
 * other service.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EntityScan(basePackages = "com.lendbridge")
@EnableJpaRepositories(basePackages = "com.lendbridge")
public class MarketplaceServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarketplaceServiceApplication.class, args);
  }
}

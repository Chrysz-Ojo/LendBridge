package com.lendbridge.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans (e.g. {@code GlobalExceptionHandler}) get picked up.
 *
 * <p>{@code @EntityScan}/{@code @EnableJpaRepositories} are needed for the same reason:
 * Spring Boot's default entity/repository scanning is anchored to this class's own package,
 * independent of the {@code @ComponentScan} above, so {@code com.lendbridge.starter.outbox}'s
 * {@code OutboxEvent} entity and {@code OutboxEventRepository} would otherwise be invisible.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EntityScan(basePackages = "com.lendbridge")
@EnableJpaRepositories(basePackages = "com.lendbridge")
@EnableScheduling
public class UserProfileServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserProfileServiceApplication.class, args);
  }
}

package com.lendbridge.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans ({@code GlobalExceptionHandler}) get picked up. {@code @EntityScan}/
 * {@code @EnableJpaRepositories} are needed for the same reason. No {@code @EnableScheduling} —
 * unlike every sibling with an outbox relay, this service publishes nothing.
 *
 * <p>{@code @EnableMethodSecurity} backs {@code ReportingController}'s
 * {@code @PreAuthorize("hasRole('SUPER_ADMIN')")}.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EntityScan(basePackages = "com.lendbridge")
@EnableJpaRepositories(basePackages = "com.lendbridge")
@EnableMethodSecurity
public class ReportingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReportingServiceApplication.class, args);
  }
}

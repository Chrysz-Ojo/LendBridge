package com.lendbridge.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans ({@code GlobalExceptionHandler}) get picked up. {@code @EnableMethodSecurity}
 * backs {@code AuditRecordController}'s {@code @PreAuthorize("hasRole('SUPER_ADMIN')")}. No
 * {@code @EnableScheduling} here — unlike every outbox-publishing sibling, this service never
 * publishes anything, so there's no relay poller to schedule.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EntityScan(basePackages = "com.lendbridge")
@EnableJpaRepositories(basePackages = "com.lendbridge")
@EnableMethodSecurity
public class AuditServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuditServiceApplication.class, args);
  }
}

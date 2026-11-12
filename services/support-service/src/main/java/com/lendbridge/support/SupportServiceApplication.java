package com.lendbridge.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans ({@code GlobalExceptionHandler}, {@code OutboxEventWriter}) get picked
 * up. {@code @EnableMethodSecurity} backs {@code TicketController}'s/{@code FaqController}'s
 * {@code @PreAuthorize("hasRole('SUPPORT')")} admin endpoints, same placement precedent as
 * risk-service's {@code RiskServiceApplication}.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EntityScan(basePackages = "com.lendbridge")
@EnableJpaRepositories(basePackages = "com.lendbridge")
@EnableScheduling
@EnableMethodSecurity
public class SupportServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SupportServiceApplication.class, args);
  }
}

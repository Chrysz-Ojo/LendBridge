package com.lendbridge.adminbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Scans the shared {@code com.lendbridge} root so platform-starter beans
 * ({@code GlobalExceptionHandler}, {@code OutboxEventWriter}) get picked up, same as every sibling
 * service. {@code @EnableFeignClients} is the one addition no prior service this session needed —
 * admin-bff is the first synchronous aggregator/BFF rather than an event-driven domain service
 * (see ADR 0023).
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EntityScan(basePackages = "com.lendbridge")
@EnableJpaRepositories(basePackages = "com.lendbridge")
@EnableScheduling
@EnableMethodSecurity
@EnableFeignClients(basePackages = "com.lendbridge.adminbff")
public class AdminBffServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AdminBffServiceApplication.class, args);
  }
}

package com.lendbridge.investment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans (e.g. {@code GlobalExceptionHandler}, {@code FieldCipher},
 * {@code IdempotencyOperations}, {@code OutboxEventWriter}) get picked up.
 * {@code @EntityScan}/{@code @EnableJpaRepositories} are needed for the same reason: Spring Boot's
 * default entity/repository scanning is anchored to this class's own package.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EntityScan(basePackages = "com.lendbridge")
@EnableJpaRepositories(basePackages = "com.lendbridge")
@EnableFeignClients
public class InvestmentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(InvestmentServiceApplication.class, args);
  }
}

package com.lendbridge.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans (e.g. {@code GlobalExceptionHandler}) get picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EnableScheduling
public class UserProfileServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserProfileServiceApplication.class, args);
  }
}

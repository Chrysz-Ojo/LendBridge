package com.lendbridge.authentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scans the shared {@code com.lendbridge} root, not just this service's own package, so
 * platform-starter beans (e.g. {@code GlobalExceptionHandler}) get picked up — the default
 * single-package scan wouldn't reach a sibling {@code com.lendbridge.starter} package.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.lendbridge")
@EnableScheduling
public class AuthenticationServiceApplication {

public static void main(String[] args) {
    SpringApplication.run(AuthenticationServiceApplication.class, args);
  }
}

package com.lendbridge.gateway.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain filterChain(
      ServerHttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(
                        "/api/v1/auth/register/**",
                        "/api/v1/auth/login",
                        "/api/v1/auth/login/**",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/password-reset/**",
                        "/.well-known/jwks.json")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
        .build();
  }

  /**
   * Allowed origins come from an env var, never hardcoded — different per environment, and a
   * wildcard would be wrong once this gateway fronts a real browser-facing app.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      org.springframework.core.env.Environment env) {
    String allowedOrigins = env.getProperty("gateway.cors.allowed-origins", "http://localhost:3000");

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}

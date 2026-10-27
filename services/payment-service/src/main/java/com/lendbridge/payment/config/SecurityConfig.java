package com.lendbridge.payment.config;

import com.lendbridge.starter.web.CorrelationIdFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Pure OAuth2 resource server, same as wallet-service's own {@code SecurityConfig}. Webhooks are
 * the one deliberate exception: a PSP calling {@code /api/v1/webhooks/{provider}} carries no JWT
 * at all — that endpoint is secured by per-adapter {@code verifySignature}, not Spring Security.
 * {@code @EnableMethodSecurity} backs the reconciliation endpoints' {@code @PreAuthorize},
 * reusing the same precedent already established in authentication-service.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> authorities = new ArrayList<>();
          List<?> roles = jwt.getClaimAsStringList("roles");
          if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
          }
          return authorities;
        });
    return converter;
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/v1/webhooks/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(new CorrelationIdFilter(), UsernamePasswordAuthenticationFilter.class)
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
    return http.build();
  }
}

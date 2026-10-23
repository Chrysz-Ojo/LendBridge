package com.lendbridge.authentication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mfa_enrollment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaEnrollment {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MfaMethod method;

  @Column(name = "secret_ciphertext")
  private String secretCiphertext;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MfaStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "activated_at")
  private Instant activatedAt;
}

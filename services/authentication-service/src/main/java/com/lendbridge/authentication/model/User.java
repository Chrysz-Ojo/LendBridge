package com.lendbridge.authentication.model;

import com.lendbridge.starter.jpa.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "phone_ciphertext", nullable = false)
  private String phone;

  @Column(name = "phone_hash", nullable = false, unique = true)
  private String phoneHash;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "phone_verified", nullable = false)
  private boolean phoneVerified;

  @Column(name = "mfa_enabled", nullable = false)
  private boolean mfaEnabled;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public boolean isCurrentlyLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public boolean isFullyVerified() {
    return emailVerified && phoneVerified;
  }
}

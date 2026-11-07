package com.lendbridge.notification.model;

import com.lendbridge.starter.jpa.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code phone} is PII (PRD section 6) and stored as ciphertext; {@code email}/{@code firstName}
 * are not in the PRD's PII list and stay plaintext, same as user-profile-service's own
 * {@code email}/{@code fullName}. */
@Entity
@Table(name = "contact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "email", nullable = false)
  private String email;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "phone_ciphertext")
  private String phone;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

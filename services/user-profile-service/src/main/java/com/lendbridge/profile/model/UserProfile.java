package com.lendbridge.profile.model;

import com.lendbridge.starter.jpa.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Owned entirely by this service — {@code userId} is a plain column referencing
 * authentication-service's user id, never a cross-service foreign key ("each service owns its
 * database; never read another service's tables"). Created as a shell row (userId + email only)
 * when {@code user.registered} is consumed; every other field is filled in later via partial
 * updates, per PRD E2-S1 ("partial saves allowed").
 */
@Entity
@Table(name = "user_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "full_name")
  private String fullName;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "address_line")
  private String addressLine;

  @Column(name = "city")
  private String city;

  @Column(name = "state")
  private String state;

  @Column(name = "country")
  private String country;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "phone_ciphertext")
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(name = "employment_status")
  private EmploymentStatus employmentStatus;

  @Column(name = "employer_or_business_name")
  private String employerOrBusinessName;

  @Column(name = "monthly_income_kobo")
  private Long monthlyIncomeKobo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

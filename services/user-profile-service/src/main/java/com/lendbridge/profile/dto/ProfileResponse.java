package com.lendbridge.profile.dto;

import com.lendbridge.profile.model.EmploymentStatus;
import com.lendbridge.profile.model.ProfileRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponse {
  private UUID userId;
  private String email;
  private String fullName;
  private LocalDate dateOfBirth;
  private String addressLine;
  private String city;
  private String state;
  private String country;
  private String phone;
  private EmploymentStatus employmentStatus;
  private String employerOrBusinessName;
  private Long monthlyIncomeKobo;
  private List<ProfileRole> roles;
  private Instant createdAt;
  private Instant updatedAt;
}

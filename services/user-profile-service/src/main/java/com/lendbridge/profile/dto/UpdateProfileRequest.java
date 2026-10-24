package com.lendbridge.profile.dto;

import com.lendbridge.profile.model.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Every field is optional: {@code null} means "leave unchanged" (partial saves, PRD E2-S1). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
  private String fullName;
  private LocalDate dateOfBirth;
  private String addressLine;
  private String city;
  private String state;
  private String country;
  private String phone;

  @Email private String email;

  private EmploymentStatus employmentStatus;
  private String employerOrBusinessName;

  @Min(0)
  private Long monthlyIncomeKobo;
}

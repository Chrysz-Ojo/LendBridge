package com.lendbridge.authentication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

  @NotBlank private String fullName;

  @NotBlank private String email;

  @NotBlank private String phone;

  @NotBlank private String password;

  @NotNull private Boolean acceptTerms;

  @NotNull private Boolean acceptPrivacy;
}

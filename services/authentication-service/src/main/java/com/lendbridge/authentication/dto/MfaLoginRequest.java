package com.lendbridge.authentication.dto;

import com.lendbridge.authentication.model.MfaMethod;
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
public class MfaLoginRequest {

  @NotBlank private String challengeToken;

  @NotNull private MfaMethod method;

  @NotBlank private String code;
}

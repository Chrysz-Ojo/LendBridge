package com.lendbridge.adminbff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaqRequest {
  @NotBlank private String category;
  @NotBlank private String question;
  @NotBlank private String answer;
  private Boolean published;
}

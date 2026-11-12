package com.lendbridge.support.dto;

import com.lendbridge.support.model.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code published} is nullable — omitted on create, the service defaults it to {@code true}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaqRequest {
  @NotNull private TicketCategory category;
  @NotBlank private String question;
  @NotBlank private String answer;
  private Boolean published;
}

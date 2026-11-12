package com.lendbridge.support.dto;

import com.lendbridge.support.model.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code categoryDetail} has no conditional "required if category == OTHER" validation — same
 * reasoning as loan-service's {@code CreateLoanRequest.purposeDetail}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {
  @NotBlank private String subject;
  @NotNull private TicketCategory category;
  private String categoryDetail;
  @NotBlank private String body;
  private UUID attachmentDocumentId;
}

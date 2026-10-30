package com.lendbridge.loan.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A loan-service-local shape for document-service's upload response — same reasoning as
 * {@code KycStatusResponse}: only the field loan-service actually needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {
  private UUID id;
}

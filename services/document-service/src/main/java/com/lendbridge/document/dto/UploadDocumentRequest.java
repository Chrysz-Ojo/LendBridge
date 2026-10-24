package com.lendbridge.document.dto;

import com.lendbridge.document.model.DocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadDocumentRequest {

  @NotNull private DocumentType documentType;
}

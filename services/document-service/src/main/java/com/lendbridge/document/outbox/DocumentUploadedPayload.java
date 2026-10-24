package com.lendbridge.document.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JSON shape stored in the outbox row; mirrors the fields of the document_uploaded.avsc
 * schema. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadedPayload {
  private String documentId;
  private String ownerId;
  private String documentType;
  private long uploadedAtEpochMillis;
}

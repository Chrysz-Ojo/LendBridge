package com.lendbridge.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostJournalEntryRequest {

  @NotBlank private String currency;
  private String reference;
  private String description;

  @NotNull
  @Valid
  @Size(min = 2, message = "A journal entry needs at least two postings")
  private List<PostingRequest> postings;
}

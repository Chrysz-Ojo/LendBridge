package com.lendbridge.ledger.mapper;

import com.lendbridge.ledger.dto.JournalEntryResponse;
import com.lendbridge.ledger.model.JournalEntry;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

/** {@code postings} is assembled by the service layer (it comes from a separate query against
 * {@code Posting}, not a JPA relationship on {@link JournalEntry}) — never by this mapper. */
@Mapper(componentModel = "spring")
public interface JournalEntryMapper {

  @Mapping(target = "postings", ignore = true)
  JournalEntryResponse toResponse(JournalEntry journalEntry);
}

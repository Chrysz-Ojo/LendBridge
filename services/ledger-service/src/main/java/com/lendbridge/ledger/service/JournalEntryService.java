package com.lendbridge.ledger.service;

import com.lendbridge.ledger.dto.JournalEntryResponse;
import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import java.util.UUID;

public interface JournalEntryService {

  JournalEntryResponse postJournalEntry(PostJournalEntryRequest request, String idempotencyKey);

  JournalEntryResponse getJournalEntry(UUID id);
}

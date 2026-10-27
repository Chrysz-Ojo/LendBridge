package com.lendbridge.ledger.controller;

import com.lendbridge.ledger.dto.JournalEntryResponse;
import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import com.lendbridge.ledger.service.JournalEntryService;
import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.exception.BadRequestException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

  private final JournalEntryService journalEntryService;

  @PostMapping
  public ResponseEntity<ApiResponse<JournalEntryResponse>> postJournalEntry(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PostJournalEntryRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    }
    JournalEntryResponse response = journalEntryService.postJournalEntry(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<JournalEntryResponse>> getJournalEntry(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(journalEntryService.getJournalEntry(id)));
  }
}

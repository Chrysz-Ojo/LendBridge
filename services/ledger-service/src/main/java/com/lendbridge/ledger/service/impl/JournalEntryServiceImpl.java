package com.lendbridge.ledger.service.impl;

import com.lendbridge.ledger.dto.JournalEntryResponse;
import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import com.lendbridge.ledger.dto.PostingRequest;
import com.lendbridge.ledger.mapper.JournalEntryMapper;
import com.lendbridge.ledger.mapper.PostingMapper;
import com.lendbridge.ledger.model.Account;
import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.model.JournalEntry;
import com.lendbridge.ledger.model.Posting;
import com.lendbridge.ledger.outbox.KafkaTopics;
import com.lendbridge.ledger.outbox.LedgerPostedPayload;
import com.lendbridge.ledger.repository.AccountRepository;
import com.lendbridge.ledger.repository.JournalEntryRepository;
import com.lendbridge.ledger.repository.PostingRepository;
import com.lendbridge.ledger.service.JournalEntryService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.starter.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JournalEntryServiceImpl implements JournalEntryService {

  private final JournalEntryRepository journalEntryRepository;
  private final PostingRepository postingRepository;
  private final AccountRepository accountRepository;
  private final JournalEntryMapper journalEntryMapper;
  private final PostingMapper postingMapper;
  private final OutboxEventWriter outboxEventWriter;
  private final IdempotencyOperations idempotencyOperations;

  @Override
  @Transactional
  public JournalEntryResponse postJournalEntry(PostJournalEntryRequest request, String idempotencyKey) {
    return idempotencyOperations.runIdempotently(
        () -> journalEntryRepository.findByIdempotencyKey(idempotencyKey).map(this::toResponse),
        () -> createJournalEntry(request, idempotencyKey));
  }

  private JournalEntryResponse createJournalEntry(PostJournalEntryRequest request, String idempotencyKey) {
    List<UUID> accountIds = request.getPostings().stream().map(PostingRequest::getAccountId).distinct().toList();
    List<Account> accounts = accountRepository.findAllById(accountIds);
    if (accounts.size() != accountIds.size()) {
      throw new NotFoundException("ACCOUNT_NOT_FOUND", "One or more referenced accounts do not exist");
    }
    for (Account account : accounts) {
      if (!account.getCurrency().equals(request.getCurrency())) {
        throw new UnprocessableEntityException(
            "CURRENCY_MISMATCH",
            "Account " + account.getId() + " currency does not match the journal entry currency");
      }
    }

    long debitTotal = sumByDirection(request.getPostings(), DebitCredit.DEBIT);
    long creditTotal = sumByDirection(request.getPostings(), DebitCredit.CREDIT);
    if (debitTotal != creditTotal) {
      throw new UnprocessableEntityException(
          "JOURNAL_ENTRY_UNBALANCED", "Sum of debits must equal sum of credits");
    }

    UUID journalEntryId = UuidV7.generate();
    Instant now = Instant.now();
    JournalEntry journalEntry =
        JournalEntry.builder()
            .id(journalEntryId)
            .idempotencyKey(idempotencyKey)
            .currency(request.getCurrency())
            .reference(request.getReference())
            .description(request.getDescription())
            .createdAt(now)
            .build();

    List<Posting> postings =
        request.getPostings().stream()
            .map(
                p ->
                    Posting.builder()
                        .id(UuidV7.generate())
                        .journalEntryId(journalEntryId)
                        .accountId(p.getAccountId())
                        .direction(p.getDirection())
                        .amountKobo(p.getAmountKobo())
                        .createdAt(now)
                        .build())
            .toList();

    journalEntryRepository.save(journalEntry);
    postingRepository.saveAll(postings);

    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    outboxEventWriter.write(
        "JournalEntry",
        journalEntryId.toString(),
        KafkaTopics.LEDGER_POSTED,
        LedgerPostedPayload.from(journalEntry, postings, correlationId));

    return buildResponse(journalEntry, postings);
  }

  @Override
  @Transactional(readOnly = true)
  public JournalEntryResponse getJournalEntry(UUID id) {
    JournalEntry journalEntry =
        journalEntryRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JOURNAL_ENTRY_NOT_FOUND", "Journal entry not found"));
    return toResponse(journalEntry);
  }

  private JournalEntryResponse toResponse(JournalEntry journalEntry) {
    List<Posting> postings = postingRepository.findAllByJournalEntryIdOrderByIdAsc(journalEntry.getId());
    return buildResponse(journalEntry, postings);
  }

  private JournalEntryResponse buildResponse(JournalEntry journalEntry, List<Posting> postings) {
    JournalEntryResponse response = journalEntryMapper.toResponse(journalEntry);
    response.setPostings(postingMapper.toResponseList(postings));
    return response;
  }

  private long sumByDirection(List<PostingRequest> postings, DebitCredit direction) {
    return postings.stream()
        .filter(p -> p.getDirection() == direction)
        .mapToLong(PostingRequest::getAmountKobo)
        .sum();
  }
}

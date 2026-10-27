package com.lendbridge.ledger.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.ledger.dto.JournalEntryResponse;
import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import com.lendbridge.ledger.dto.PostingRequest;
import com.lendbridge.ledger.mapper.JournalEntryMapper;
import com.lendbridge.ledger.mapper.PostingMapper;
import com.lendbridge.ledger.model.Account;
import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.model.JournalEntry;
import com.lendbridge.ledger.repository.AccountRepository;
import com.lendbridge.ledger.repository.JournalEntryRepository;
import com.lendbridge.ledger.repository.PostingRepository;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JournalEntryServiceImplTest {

  @Mock private JournalEntryRepository journalEntryRepository;
  @Mock private PostingRepository postingRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private OutboxEventWriter outboxEventWriter;
  private JournalEntryMapper journalEntryMapper;
  private PostingMapper postingMapper;

  private JournalEntryServiceImpl service;

  @BeforeEach
  void setUp() {
    journalEntryMapper = Mappers.getMapper(JournalEntryMapper.class);
    postingMapper = Mappers.getMapper(PostingMapper.class);
    service =
        new JournalEntryServiceImpl(
            journalEntryRepository,
            postingRepository,
            accountRepository,
            journalEntryMapper,
            postingMapper,
            outboxEventWriter,
            new IdempotencyOperations());
  }

  @Test
  void postJournalEntry_rejectsUnbalancedEntry() {
    when(journalEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    UUID accountA = UUID.randomUUID();
    UUID accountB = UUID.randomUUID();
    when(accountRepository.findAllById(any())).thenReturn(List.of(account(accountA), account(accountB)));

    PostJournalEntryRequest request =
        request(
            new PostingRequest(accountA, DebitCredit.DEBIT, 1_000),
            new PostingRequest(accountB, DebitCredit.CREDIT, 500));

    assertThatThrownBy(() -> service.postJournalEntry(request, "key-1"))
        .isInstanceOf(UnprocessableEntityException.class);
    verify(journalEntryRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void postJournalEntry_rejectsUnknownAccount() {
    when(journalEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    when(accountRepository.findAllById(any())).thenReturn(List.of());

    PostJournalEntryRequest request =
        request(
            new PostingRequest(UUID.randomUUID(), DebitCredit.DEBIT, 1_000),
            new PostingRequest(UUID.randomUUID(), DebitCredit.CREDIT, 1_000));

    assertThatThrownBy(() -> service.postJournalEntry(request, "key-1")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void postJournalEntry_rejectsCurrencyMismatch() {
    when(journalEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    UUID accountA = UUID.randomUUID();
    UUID accountB = UUID.randomUUID();
    Account mismatched = account(accountB);
    mismatched.setCurrency("USD");
    when(accountRepository.findAllById(any())).thenReturn(List.of(account(accountA), mismatched));

    PostJournalEntryRequest request =
        request(
            new PostingRequest(accountA, DebitCredit.DEBIT, 1_000),
            new PostingRequest(accountB, DebitCredit.CREDIT, 1_000));

    assertThatThrownBy(() -> service.postJournalEntry(request, "key-1"))
        .isInstanceOf(UnprocessableEntityException.class);
  }

  @Test
  void postJournalEntry_happyPath_savesAndPublishes() {
    when(journalEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    UUID accountA = UUID.randomUUID();
    UUID accountB = UUID.randomUUID();
    when(accountRepository.findAllById(any())).thenReturn(List.of(account(accountA), account(accountB)));

    PostJournalEntryRequest request =
        request(
            new PostingRequest(accountA, DebitCredit.DEBIT, 1_000),
            new PostingRequest(accountB, DebitCredit.CREDIT, 1_000));

    JournalEntryResponse response = service.postJournalEntry(request, "key-1");

    assertThat(response.getCurrency()).isEqualTo("NGN");
    assertThat(response.getPostings()).hasSize(2);
    verify(journalEntryRepository).save(any(JournalEntry.class));
    verify(postingRepository).saveAll(any());
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void postJournalEntry_idempotentReplay_returnsExistingWithoutSideEffects() {
    JournalEntry existing =
        JournalEntry.builder()
            .id(UUID.randomUUID())
            .idempotencyKey("key-1")
            .currency("NGN")
            .createdAt(Instant.now())
            .build();
    when(journalEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
    when(postingRepository.findAllByJournalEntryIdOrderByIdAsc(existing.getId())).thenReturn(List.of());

    PostJournalEntryRequest request =
        request(
            new PostingRequest(UUID.randomUUID(), DebitCredit.DEBIT, 1_000),
            new PostingRequest(UUID.randomUUID(), DebitCredit.CREDIT, 1_000));

    JournalEntryResponse response = service.postJournalEntry(request, "key-1");

    assertThat(response.getId()).isEqualTo(existing.getId());
    verify(journalEntryRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void getJournalEntry_throwsNotFound_whenMissing() {
    UUID id = UUID.randomUUID();
    when(journalEntryRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getJournalEntry(id)).isInstanceOf(NotFoundException.class);
  }

  private Account account(UUID id) {
    return Account.builder()
        .id(id)
        .accountType("WALLET")
        .currency("NGN")
        .normalBalance(DebitCredit.DEBIT)
        .createdAt(Instant.now())
        .build();
  }

  private PostJournalEntryRequest request(PostingRequest... postings) {
    PostJournalEntryRequest request = new PostJournalEntryRequest();
    request.setCurrency("NGN");
    request.setPostings(List.of(postings));
    return request;
  }
}

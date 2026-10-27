package com.lendbridge.ledger.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.ledger.dto.AccountBalanceResponse;
import com.lendbridge.ledger.dto.AccountResponse;
import com.lendbridge.ledger.dto.OpenAccountRequest;
import com.lendbridge.ledger.dto.PostingPageResponse;
import com.lendbridge.ledger.mapper.AccountMapper;
import com.lendbridge.ledger.mapper.PostingMapper;
import com.lendbridge.ledger.model.Account;
import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.model.Posting;
import com.lendbridge.ledger.repository.AccountRepository;
import com.lendbridge.ledger.repository.PostingRepository;
import com.lendbridge.starter.exception.NotFoundException;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

  @Mock private AccountRepository accountRepository;
  @Mock private PostingRepository postingRepository;
  private AccountMapper accountMapper;
  private PostingMapper postingMapper;

  private AccountServiceImpl service;

  @BeforeEach
  void setUp() {
    accountMapper = Mappers.getMapper(AccountMapper.class);
    postingMapper = Mappers.getMapper(PostingMapper.class);
    service = new AccountServiceImpl(accountRepository, postingRepository, accountMapper, postingMapper);
  }

  @Test
  void openAccount_savesAndReturnsAccount() {
    OpenAccountRequest request = new OpenAccountRequest("WALLET", "NGN", DebitCredit.DEBIT, "user-1");

    AccountResponse response = service.openAccount(request);

    assertThat(response.getAccountType()).isEqualTo("WALLET");
    assertThat(response.getCurrency()).isEqualTo("NGN");
    assertThat(response.getNormalBalance()).isEqualTo(DebitCredit.DEBIT);
    assertThat(response.getOwnerReference()).isEqualTo("user-1");
    assertThat(response.getId()).isNotNull();
    verify(accountRepository).save(any(Account.class));
  }

  @Test
  void getAccount_throwsNotFound_whenMissing() {
    UUID id = UUID.randomUUID();
    when(accountRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAccount(id)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getBalance_debitNormalAccount_isDebitMinusCredit() {
    UUID id = UUID.randomUUID();
    when(accountRepository.findById(id)).thenReturn(Optional.of(account(id, DebitCredit.DEBIT)));
    when(postingRepository.sumAmountByAccountIdAndDirection(id, DebitCredit.DEBIT)).thenReturn(10_000L);
    when(postingRepository.sumAmountByAccountIdAndDirection(id, DebitCredit.CREDIT)).thenReturn(4_000L);

    AccountBalanceResponse response = service.getBalance(id);

    assertThat(response.getBalanceKobo()).isEqualTo(6_000L);
  }

  @Test
  void getBalance_creditNormalAccount_isCreditMinusDebit() {
    UUID id = UUID.randomUUID();
    when(accountRepository.findById(id)).thenReturn(Optional.of(account(id, DebitCredit.CREDIT)));
    when(postingRepository.sumAmountByAccountIdAndDirection(id, DebitCredit.DEBIT)).thenReturn(1_000L);
    when(postingRepository.sumAmountByAccountIdAndDirection(id, DebitCredit.CREDIT)).thenReturn(9_000L);

    AccountBalanceResponse response = service.getBalance(id);

    assertThat(response.getBalanceKobo()).isEqualTo(8_000L);
  }

  @Test
  void getBalance_throwsNotFound_whenAccountMissing() {
    UUID id = UUID.randomUUID();
    when(accountRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBalance(id)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void listPostings_returnsNextCursor_whenPageIsFull() {
    UUID accountId = UUID.randomUUID();
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, DebitCredit.DEBIT)));
    Posting p1 = posting(accountId);
    Posting p2 = posting(accountId);
    when(postingRepository.findAllByAccountIdOrderByIdAsc(accountId, PageRequest.of(0, 2)))
        .thenReturn(List.of(p1, p2));

    PostingPageResponse response = service.listPostings(accountId, null, 2);

    assertThat(response.getPostings()).hasSize(2);
    assertThat(response.getNextCursor()).isEqualTo(p2.getId());
  }

  @Test
  void listPostings_returnsNullCursor_whenPageIsPartial() {
    UUID accountId = UUID.randomUUID();
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(accountId, DebitCredit.DEBIT)));
    when(postingRepository.findAllByAccountIdOrderByIdAsc(accountId, PageRequest.of(0, 50)))
        .thenReturn(List.of(posting(accountId)));

    PostingPageResponse response = service.listPostings(accountId, null, 50);

    assertThat(response.getNextCursor()).isNull();
  }

  @Test
  void listPostings_throwsNotFound_whenAccountMissing() {
    UUID accountId = UUID.randomUUID();
    when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listPostings(accountId, null, 50)).isInstanceOf(NotFoundException.class);
  }

  private Account account(UUID id, DebitCredit normalBalance) {
    return Account.builder()
        .id(id)
        .accountType("WALLET")
        .currency("NGN")
        .normalBalance(normalBalance)
        .createdAt(Instant.now())
        .build();
  }

  private Posting posting(UUID accountId) {
    return Posting.builder()
        .id(UUID.randomUUID())
        .journalEntryId(UUID.randomUUID())
        .accountId(accountId)
        .direction(DebitCredit.DEBIT)
        .amountKobo(100)
        .createdAt(Instant.now())
        .build();
  }
}

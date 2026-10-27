package com.lendbridge.ledger.service.impl;

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
import com.lendbridge.ledger.service.AccountService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

  private final AccountRepository accountRepository;
  private final PostingRepository postingRepository;
  private final AccountMapper accountMapper;
  private final PostingMapper postingMapper;

  @Override
  @Transactional
  public AccountResponse openAccount(OpenAccountRequest request) {
    Account account =
        Account.builder()
            .id(UuidV7.generate())
            .accountType(request.getAccountType())
            .currency(request.getCurrency())
            .normalBalance(request.getNormalBalance())
            .ownerReference(request.getOwnerReference())
            .createdAt(Instant.now())
            .build();
    accountRepository.save(account);
    return accountMapper.toResponse(account);
  }

  @Override
  @Transactional(readOnly = true)
  public AccountResponse getAccount(UUID id) {
    return accountMapper.toResponse(findAccountOrThrow(id));
  }

  @Override
  @Transactional(readOnly = true)
  public AccountBalanceResponse getBalance(UUID id) {
    Account account = findAccountOrThrow(id);
    long debitTotal = postingRepository.sumAmountByAccountIdAndDirection(id, DebitCredit.DEBIT);
    long creditTotal = postingRepository.sumAmountByAccountIdAndDirection(id, DebitCredit.CREDIT);
    long balance =
        account.getNormalBalance() == DebitCredit.DEBIT
            ? debitTotal - creditTotal
            : creditTotal - debitTotal;
    return AccountBalanceResponse.builder()
        .accountId(id)
        .currency(account.getCurrency())
        .balanceKobo(balance)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public PostingPageResponse listPostings(UUID accountId, UUID after, int limit) {
    findAccountOrThrow(accountId);
    List<Posting> postings =
        after == null
            ? postingRepository.findAllByAccountIdOrderByIdAsc(accountId, PageRequest.of(0, limit))
            : postingRepository.findAllByAccountIdAndIdGreaterThanOrderByIdAsc(
                accountId, after, PageRequest.of(0, limit));
    UUID nextCursor = postings.size() == limit ? postings.get(postings.size() - 1).getId() : null;
    return PostingPageResponse.builder()
        .postings(postingMapper.toResponseList(postings))
        .nextCursor(nextCursor)
        .build();
  }

  private Account findAccountOrThrow(UUID id) {
    return accountRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Account not found"));
  }
}

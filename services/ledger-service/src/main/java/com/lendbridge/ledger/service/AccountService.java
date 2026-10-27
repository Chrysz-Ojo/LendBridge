package com.lendbridge.ledger.service;

import com.lendbridge.ledger.dto.AccountBalanceResponse;
import com.lendbridge.ledger.dto.AccountResponse;
import com.lendbridge.ledger.dto.OpenAccountRequest;
import com.lendbridge.ledger.dto.PostingPageResponse;
import java.util.UUID;

public interface AccountService {

  AccountResponse openAccount(OpenAccountRequest request);

  AccountResponse getAccount(UUID id);

  AccountBalanceResponse getBalance(UUID id);

  PostingPageResponse listPostings(UUID accountId, UUID after, int limit);
}

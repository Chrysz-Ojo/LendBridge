package com.lendbridge.wallet.service;

import com.lendbridge.wallet.dto.AddBankAccountRequest;
import com.lendbridge.wallet.dto.BankAccountResponse;
import java.util.List;
import java.util.UUID;

public interface BankAccountService {

  BankAccountResponse addBankAccount(UUID userId, AddBankAccountRequest request);

  List<BankAccountResponse> listBankAccounts(UUID userId);
}

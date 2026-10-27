package com.lendbridge.wallet.service;

import com.lendbridge.wallet.dto.AddBankAccountRequest;
import com.lendbridge.wallet.dto.BankAccountPayoutDetailsResponse;
import com.lendbridge.wallet.dto.BankAccountResponse;
import java.util.List;
import java.util.UUID;

public interface BankAccountService {

  BankAccountResponse addBankAccount(UUID userId, AddBankAccountRequest request);

  List<BankAccountResponse> listBankAccounts(UUID userId);

  /** Decrypted account number, for payout construction only — never for display. */
  BankAccountPayoutDetailsResponse getBankAccountForPayout(UUID userId, UUID bankAccountId);
}

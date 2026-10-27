package com.lendbridge.wallet.service.impl;

import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.dto.AddBankAccountRequest;
import com.lendbridge.wallet.dto.BankAccountResponse;
import com.lendbridge.wallet.model.BankAccount;
import com.lendbridge.wallet.model.BankVerificationStatus;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletBankAccountVerifiedPayload;
import com.lendbridge.wallet.repository.BankAccountRepository;
import com.lendbridge.wallet.service.BankAccountService;
import com.lendbridge.wallet.service.WalletService;
import com.lendbridge.wallet.verification.BankAccountVerificationProvider;
import com.lendbridge.wallet.verification.BankAccountVerificationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

  private final BankAccountRepository bankAccountRepository;
  private final WalletService walletService;
  private final BankAccountVerificationProvider verificationProvider;
  private final OutboxEventWriter outboxEventWriter;

  @Override
  @Transactional
  public BankAccountResponse addBankAccount(UUID userId, AddBankAccountRequest request) {
    Wallet wallet = walletService.getOrCreateWallet(userId);

    List<BankAccount> existing = bankAccountRepository.findAllByWalletId(wallet.getId());
    // account_number is AES-GCM encrypted with a fresh IV per row, so a DB-level unique
    // constraint on the ciphertext can never catch a duplicate plaintext number — compare after
    // decryption instead (JPA decrypts transparently on read via EncryptedStringConverter).
    boolean duplicate =
        existing.stream()
            .anyMatch(account -> account.getAccountNumber().equals(request.getAccountNumber()));
    if (duplicate) {
      throw new ConflictException(
          "BANK_ACCOUNT_ALREADY_ADDED", "This bank account has already been added");
    }

    BankAccountVerificationResult verification =
        verificationProvider.verify(
            request.getAccountNumber(), request.getBankCode(), request.getAccountHolderName());

    Instant now = Instant.now();
    BankAccount bankAccount =
        BankAccount.builder()
            .id(UuidV7.generate())
            .walletId(wallet.getId())
            .bankCode(request.getBankCode())
            .bankName(request.getBankName())
            .accountNumber(request.getAccountNumber())
            .accountHolderName(request.getAccountHolderName())
            .verificationStatus(
                verification.isVerified()
                    ? BankVerificationStatus.VERIFIED
                    : BankVerificationStatus.REJECTED)
            .verifiedAt(verification.isVerified() ? now : null)
            .createdAt(now)
            .build();
    bankAccountRepository.save(bankAccount);

    if (verification.isVerified()) {
      outboxEventWriter.write(
          "BankAccount",
          bankAccount.getId().toString(),
          KafkaTopics.WALLET_BANK_ACCOUNT_VERIFIED,
          new WalletBankAccountVerifiedPayload(
              bankAccount.getId().toString(),
              wallet.getId().toString(),
              userId.toString(),
              now.toEpochMilli()));
    }

    return toResponse(bankAccount);
  }

  @Override
  @Transactional(readOnly = true)
  public List<BankAccountResponse> listBankAccounts(UUID userId) {
    // findWallet, not getOrCreateWallet: read-only transaction, and no wallet row yet just means
    // no bank accounts exist yet.
    return walletService
        .findWallet(userId)
        .map(
            wallet ->
                bankAccountRepository.findAllByWalletId(wallet.getId()).stream()
                    .map(this::toResponse)
                    .toList())
        .orElseGet(List::of);
  }

  private BankAccountResponse toResponse(BankAccount bankAccount) {
    return BankAccountResponse.builder()
        .id(bankAccount.getId())
        .bankCode(bankAccount.getBankCode())
        .bankName(bankAccount.getBankName())
        .maskedAccountNumber(mask(bankAccount.getAccountNumber()))
        .accountHolderName(bankAccount.getAccountHolderName())
        .verificationStatus(bankAccount.getVerificationStatus())
        .verifiedAt(bankAccount.getVerifiedAt())
        .createdAt(bankAccount.getCreatedAt())
        .build();
  }

  private String mask(String accountNumber) {
    if (accountNumber.length() <= 4) {
      return "****";
    }
    return "*".repeat(accountNumber.length() - 4)
        + accountNumber.substring(accountNumber.length() - 4);
  }
}

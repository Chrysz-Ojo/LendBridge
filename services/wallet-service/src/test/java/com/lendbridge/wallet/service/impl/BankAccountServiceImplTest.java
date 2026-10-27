package com.lendbridge.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.dto.AddBankAccountRequest;
import com.lendbridge.wallet.dto.BankAccountResponse;
import com.lendbridge.wallet.model.BankAccount;
import com.lendbridge.wallet.model.BankVerificationStatus;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.repository.BankAccountRepository;
import com.lendbridge.wallet.service.WalletService;
import com.lendbridge.wallet.verification.BankAccountVerificationProvider;
import com.lendbridge.wallet.verification.FakeBankAccountVerificationProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceImplTest {

  @Mock private BankAccountRepository bankAccountRepository;
  @Mock private WalletService walletService;
  @Mock private OutboxEventWriter outboxEventWriter;
  private BankAccountVerificationProvider verificationProvider;

  private BankAccountServiceImpl service;
  private UUID userId;
  private Wallet wallet;

  @BeforeEach
  void setUp() {
    verificationProvider = new FakeBankAccountVerificationProvider();
    service =
        new BankAccountServiceImpl(
            bankAccountRepository, walletService, verificationProvider, outboxEventWriter);
    userId = UUID.randomUUID();
    wallet =
        Wallet.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .currency("NGN")
            .ledgerAccountId(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Test
  void addBankAccount_verifiedAccount_publishesEvent() {
    when(walletService.getOrCreateWallet(userId)).thenReturn(wallet);
    when(bankAccountRepository.findAllByWalletId(wallet.getId())).thenReturn(List.of());
    AddBankAccountRequest request =
        new AddBankAccountRequest("058", "GTBank", "0123456789", "Jane Doe");

    BankAccountResponse response = service.addBankAccount(userId, request);

    assertThat(response.getVerificationStatus()).isEqualTo(BankVerificationStatus.VERIFIED);
    assertThat(response.getMaskedAccountNumber()).isEqualTo("******6789");
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void addBankAccount_denyListedAccount_rejectedWithoutEvent() {
    when(walletService.getOrCreateWallet(userId)).thenReturn(wallet);
    when(bankAccountRepository.findAllByWalletId(wallet.getId())).thenReturn(List.of());
    AddBankAccountRequest request =
        new AddBankAccountRequest("058", "GTBank", "0000000001", "Jane Doe");

    BankAccountResponse response = service.addBankAccount(userId, request);

    assertThat(response.getVerificationStatus()).isEqualTo(BankVerificationStatus.REJECTED);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void addBankAccount_rejectsDuplicateAccountNumberForSameWallet() {
    when(walletService.getOrCreateWallet(userId)).thenReturn(wallet);
    BankAccount existing =
        BankAccount.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .bankCode("058")
            .accountNumber("0123456789")
            .accountHolderName("Jane Doe")
            .verificationStatus(BankVerificationStatus.VERIFIED)
            .createdAt(Instant.now())
            .build();
    when(bankAccountRepository.findAllByWalletId(wallet.getId())).thenReturn(List.of(existing));
    AddBankAccountRequest request =
        new AddBankAccountRequest("058", "GTBank", "0123456789", "Jane Doe");

    assertThatThrownBy(() -> service.addBankAccount(userId, request))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void listBankAccounts_returnsEmpty_whenWalletNotYetProvisioned() {
    when(walletService.findWallet(userId)).thenReturn(java.util.Optional.empty());

    assertThat(service.listBankAccounts(userId)).isEmpty();
    verify(walletService, never()).getOrCreateWallet(any());
  }

  @Test
  void getBankAccountForPayout_returnsDecryptedAccountNumber() {
    BankAccount account =
        BankAccount.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .bankCode("058")
            .bankName("GTBank")
            .accountNumber("0123456789")
            .accountHolderName("Jane Doe")
            .verificationStatus(BankVerificationStatus.VERIFIED)
            .createdAt(Instant.now())
            .build();
    when(walletService.findWallet(userId)).thenReturn(java.util.Optional.of(wallet));
    when(bankAccountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));

    com.lendbridge.wallet.dto.BankAccountPayoutDetailsResponse response =
        service.getBankAccountForPayout(userId, account.getId());

    assertThat(response.getAccountNumber()).isEqualTo("0123456789");
    assertThat(response.getVerificationStatus()).isEqualTo(BankVerificationStatus.VERIFIED);
  }

  @Test
  void getBankAccountForPayout_throwsNotFound_whenAccountBelongsToAnotherWallet() {
    Wallet otherWallet =
        Wallet.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .currency("NGN")
            .ledgerAccountId(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    BankAccount account =
        BankAccount.builder()
            .id(UUID.randomUUID())
            .walletId(otherWallet.getId())
            .bankCode("058")
            .accountNumber("0123456789")
            .accountHolderName("Someone Else")
            .verificationStatus(BankVerificationStatus.VERIFIED)
            .createdAt(Instant.now())
            .build();
    when(walletService.findWallet(userId)).thenReturn(java.util.Optional.of(wallet));
    when(bankAccountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));

    assertThatThrownBy(() -> service.getBankAccountForPayout(userId, account.getId()))
        .isInstanceOf(com.lendbridge.starter.exception.NotFoundException.class);
  }
}

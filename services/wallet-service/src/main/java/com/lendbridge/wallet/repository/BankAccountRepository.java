package com.lendbridge.wallet.repository;

import com.lendbridge.wallet.model.BankAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

  List<BankAccount> findAllByWalletId(UUID walletId);
}

package com.lendbridge.wallet.repository;

import com.lendbridge.wallet.model.VirtualAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

  List<VirtualAccount> findAllByWalletId(UUID walletId);
}

package com.lendbridge.ledger.repository;

import com.lendbridge.ledger.model.Account;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {}

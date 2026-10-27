package com.lendbridge.wallet.service.impl;

import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.wallet.dto.RegisterVirtualAccountRequest;
import com.lendbridge.wallet.dto.VirtualAccountResponse;
import com.lendbridge.wallet.mapper.VirtualAccountMapper;
import com.lendbridge.wallet.model.VirtualAccount;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.repository.VirtualAccountRepository;
import com.lendbridge.wallet.service.VirtualAccountService;
import com.lendbridge.wallet.service.WalletService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pure storage — Payment Service (not yet built) owns actually provisioning this with a PSP. */
@Service
@RequiredArgsConstructor
public class VirtualAccountServiceImpl implements VirtualAccountService {

  private final VirtualAccountRepository virtualAccountRepository;
  private final WalletService walletService;
  private final VirtualAccountMapper virtualAccountMapper;

  @Override
  @Transactional
  public VirtualAccountResponse registerVirtualAccount(
      UUID userId, RegisterVirtualAccountRequest request) {
    Wallet wallet = walletService.getOrCreateWallet(userId);
    VirtualAccount virtualAccount =
        VirtualAccount.builder()
            .id(UuidV7.generate())
            .walletId(wallet.getId())
            .bankName(request.getBankName())
            .accountNumber(request.getAccountNumber())
            .providerReference(request.getProviderReference())
            .createdAt(Instant.now())
            .build();
    virtualAccountRepository.save(virtualAccount);
    return virtualAccountMapper.toResponse(virtualAccount);
  }

  @Override
  @Transactional(readOnly = true)
  public List<VirtualAccountResponse> listVirtualAccounts(UUID userId) {
    // findWallet, not getOrCreateWallet: read-only transaction, and no wallet row yet just means
    // no virtual accounts exist yet.
    Optional<Wallet> wallet = walletService.findWallet(userId);
    if (wallet.isEmpty()) {
      return List.of();
    }
    return virtualAccountMapper.toResponseList(
        virtualAccountRepository.findAllByWalletId(wallet.get().getId()));
  }
}

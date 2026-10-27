package com.lendbridge.wallet.service;

import com.lendbridge.wallet.dto.RegisterVirtualAccountRequest;
import com.lendbridge.wallet.dto.VirtualAccountResponse;
import java.util.List;
import java.util.UUID;

public interface VirtualAccountService {

  VirtualAccountResponse registerVirtualAccount(UUID userId, RegisterVirtualAccountRequest request);

  List<VirtualAccountResponse> listVirtualAccounts(UUID userId);
}

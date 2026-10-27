package com.lendbridge.wallet.mapper;

import com.lendbridge.wallet.dto.VirtualAccountResponse;
import com.lendbridge.wallet.model.VirtualAccount;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VirtualAccountMapper {

  VirtualAccountResponse toResponse(VirtualAccount virtualAccount);

  List<VirtualAccountResponse> toResponseList(List<VirtualAccount> virtualAccounts);
}

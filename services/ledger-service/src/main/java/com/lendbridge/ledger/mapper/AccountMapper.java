package com.lendbridge.ledger.mapper;

import com.lendbridge.ledger.dto.AccountResponse;
import com.lendbridge.ledger.model.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

  AccountResponse toResponse(Account account);
}

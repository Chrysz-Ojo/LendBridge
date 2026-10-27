package com.lendbridge.payment.mapper;

import com.lendbridge.payment.dto.WithdrawalResponse;
import com.lendbridge.payment.model.Payout;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WithdrawalMapper {

  WithdrawalResponse toResponse(Payout payout);
}

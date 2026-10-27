package com.lendbridge.payment.mapper;

import com.lendbridge.payment.dto.TopUpResponse;
import com.lendbridge.payment.model.PaymentIntent;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TopUpMapper {

  TopUpResponse toResponse(PaymentIntent paymentIntent);
}

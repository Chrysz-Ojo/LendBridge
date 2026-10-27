package com.lendbridge.payment.mapper;

import com.lendbridge.payment.dto.ReconciliationRunResponse;
import com.lendbridge.payment.model.ReconciliationRun;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReconciliationRunMapper {

  ReconciliationRunResponse toResponse(ReconciliationRun reconciliationRun);
}

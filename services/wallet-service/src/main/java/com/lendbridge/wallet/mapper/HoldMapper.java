package com.lendbridge.wallet.mapper;

import com.lendbridge.wallet.dto.HoldResponse;
import com.lendbridge.wallet.model.Hold;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HoldMapper {

  HoldResponse toResponse(Hold hold);

  List<HoldResponse> toResponseList(List<Hold> holds);
}

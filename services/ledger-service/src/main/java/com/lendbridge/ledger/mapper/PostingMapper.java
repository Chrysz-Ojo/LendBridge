package com.lendbridge.ledger.mapper;

import com.lendbridge.ledger.dto.PostingResponse;
import com.lendbridge.ledger.model.Posting;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PostingMapper {

  PostingResponse toResponse(Posting posting);

  List<PostingResponse> toResponseList(List<Posting> postings);
}

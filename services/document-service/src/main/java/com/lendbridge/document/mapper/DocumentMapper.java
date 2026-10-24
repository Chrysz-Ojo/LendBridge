package com.lendbridge.document.mapper;

import com.lendbridge.document.dto.DocumentResponse;
import com.lendbridge.document.model.Document;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

  DocumentResponse toResponse(Document document);
}

package com.lendbridge.support.service;

import com.lendbridge.support.dto.FaqRequest;
import com.lendbridge.support.dto.FaqResponse;
import java.util.List;
import java.util.UUID;

public interface FaqService {

  List<FaqResponse> findPublished(String query);

  FaqResponse create(FaqRequest request);

  FaqResponse update(UUID faqId, FaqRequest request);

  void delete(UUID faqId);
}

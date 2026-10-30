package com.lendbridge.marketplace.service;

import com.lendbridge.marketplace.dto.ListingResponse;
import com.lendbridge.marketplace.dto.ListingSearchRequest;
import java.util.List;
import java.util.UUID;

public interface ListingService {

  List<ListingResponse> search(ListingSearchRequest request);

  ListingResponse getById(UUID id);
}

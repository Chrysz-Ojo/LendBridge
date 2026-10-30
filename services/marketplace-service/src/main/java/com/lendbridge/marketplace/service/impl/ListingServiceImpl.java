package com.lendbridge.marketplace.service.impl;

import com.lendbridge.marketplace.dto.ListingResponse;
import com.lendbridge.marketplace.dto.ListingSearchRequest;
import com.lendbridge.marketplace.model.Listing;
import com.lendbridge.marketplace.model.ListingStatus;
import com.lendbridge.marketplace.repository.ListingRepository;
import com.lendbridge.marketplace.service.ListingService;
import com.lendbridge.starter.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

  private final ListingRepository listingRepository;

  @Override
  @Transactional(readOnly = true)
  public List<ListingResponse> search(ListingSearchRequest request) {
    return listingRepository
        .search(
            request.getMinAmountKobo(),
            request.getMaxAmountKobo(),
            request.getMinRateBps(),
            request.getMaxRateBps(),
            request.getMinTenorMonths(),
            request.getMaxTenorMonths(),
            request.getPurpose())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ListingResponse getById(UUID id) {
    Listing listing =
        listingRepository
            .findById(id)
            .filter(l -> l.getStatus() == ListingStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("LISTING_NOT_FOUND", "Listing not found"));
    return toResponse(listing);
  }

  private ListingResponse toResponse(Listing listing) {
    return ListingResponse.builder()
        .id(listing.getId())
        .amountKobo(listing.getAmountKobo())
        .interestRateBps(listing.getInterestRateBps())
        .tenorMonths(listing.getTenorMonths())
        .purpose(listing.getPurpose())
        .fundedAmountKobo(listing.getFundedAmountKobo())
        .listedAt(listing.getListedAt())
        .build();
  }
}

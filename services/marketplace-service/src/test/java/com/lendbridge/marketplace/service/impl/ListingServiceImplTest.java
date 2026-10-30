package com.lendbridge.marketplace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lendbridge.marketplace.dto.ListingResponse;
import com.lendbridge.marketplace.dto.ListingSearchRequest;
import com.lendbridge.marketplace.model.Listing;
import com.lendbridge.marketplace.model.ListingStatus;
import com.lendbridge.marketplace.repository.ListingRepository;
import com.lendbridge.starter.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListingServiceImplTest {

  private ListingRepository repository;
  private ListingServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = mock(ListingRepository.class);
    service = new ListingServiceImpl(repository);
  }

  private Listing listing(ListingStatus status) {
    Instant now = Instant.now();
    return Listing.builder()
        .id(UUID.randomUUID())
        .borrowerUserId(UUID.randomUUID())
        .amountKobo(300_000L)
        .interestRateBps(1800)
        .tenorMonths(6)
        .purpose("BUSINESS")
        .status(status)
        .fundedAmountKobo(150_000L)
        .listedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Test
  void search_passesAllFiltersThroughToTheRepository() {
    when(repository.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(listing(ListingStatus.ACTIVE)));

    List<ListingResponse> results =
        service.search(
            ListingSearchRequest.builder()
                .minAmountKobo(100_000L)
                .maxAmountKobo(500_000L)
                .minRateBps(1000)
                .maxRateBps(2000)
                .minTenorMonths(1)
                .maxTenorMonths(12)
                .purpose("BUSINESS")
                .build());

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getPurpose()).isEqualTo("BUSINESS");
  }

  @Test
  void search_noFilters_stillReturnsResults() {
    when(repository.search(null, null, null, null, null, null, null)).thenReturn(List.of(listing(ListingStatus.ACTIVE)));

    List<ListingResponse> results = service.search(ListingSearchRequest.builder().build());

    assertThat(results).hasSize(1);
  }

  @Test
  void getById_activeListing_returnsIt() {
    Listing listing = listing(ListingStatus.ACTIVE);
    when(repository.findById(listing.getId())).thenReturn(Optional.of(listing));

    ListingResponse response = service.getById(listing.getId());

    assertThat(response.getId()).isEqualTo(listing.getId());
    assertThat(response.getAmountKobo()).isEqualTo(300_000L);
    assertThat(response.getFundedAmountKobo()).isEqualTo(150_000L);
  }

  @Test
  void getById_delistedListing_throwsNotFound() {
    Listing listing = listing(ListingStatus.DELISTED);
    when(repository.findById(listing.getId())).thenReturn(Optional.of(listing));

    assertThatThrownBy(() -> service.getById(listing.getId())).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getById_unknownId_throwsNotFound() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(id)).isInstanceOf(NotFoundException.class);
  }
}

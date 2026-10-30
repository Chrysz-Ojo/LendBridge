package com.lendbridge.marketplace.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.marketplace.model.Listing;
import com.lendbridge.marketplace.model.ListingStatus;
import com.lendbridge.marketplace.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListingControllerIT extends AbstractIntegrationTest {

  private Listing saveListing(long amountKobo, int rateBps, int tenorMonths, String purpose, ListingStatus status) {
    Instant now = Instant.now();
    Listing listing =
        Listing.builder()
            .id(UUID.randomUUID())
            .borrowerUserId(UUID.randomUUID())
            .amountKobo(amountKobo)
            .interestRateBps(rateBps)
            .tenorMonths(tenorMonths)
            .purpose(purpose)
            .status(status)
            .listedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    return listingRepository.save(listing);
  }

  @Test
  void search_withNoFilters_returnsOnlyActiveListings() throws Exception {
    saveListing(300_000L, 1800, 6, "BUSINESS", ListingStatus.ACTIVE);
    saveListing(150_000L, 2200, 3, "EDUCATION", ListingStatus.DELISTED);

    mockMvc
        .perform(get("/api/v1/marketplace/listings").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.purpose == 'EDUCATION')]").doesNotExist())
        .andExpect(jsonPath("$.data[?(@.purpose == 'BUSINESS')]").exists());
  }

  @Test
  void search_filtersByAmountRange() throws Exception {
    saveListing(100_000L, 1800, 6, "BUSINESS", ListingStatus.ACTIVE);
    saveListing(900_000L, 1800, 6, "BUSINESS", ListingStatus.ACTIVE);

    mockMvc
        .perform(
            get("/api/v1/marketplace/listings")
                .param("minAmountKobo", "500000")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.amountKobo == 100000)]").doesNotExist())
        .andExpect(jsonPath("$.data[?(@.amountKobo == 900000)]").exists());
  }

  @Test
  void search_filtersByPurpose() throws Exception {
    saveListing(300_000L, 1800, 6, "BUSINESS", ListingStatus.ACTIVE);
    saveListing(300_000L, 1800, 6, "MEDICAL", ListingStatus.ACTIVE);

    mockMvc
        .perform(
            get("/api/v1/marketplace/listings")
                .param("purpose", "MEDICAL")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.purpose == 'BUSINESS')]").doesNotExist())
        .andExpect(jsonPath("$.data[?(@.purpose == 'MEDICAL')]").exists());
  }

  @Test
  void getById_activeListing_returnsIt() throws Exception {
    Listing listing = saveListing(300_000L, 1800, 6, "BUSINESS", ListingStatus.ACTIVE);

    mockMvc
        .perform(
            get("/api/v1/marketplace/listings/" + listing.getId())
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id", org.hamcrest.Matchers.equalTo(listing.getId().toString())));
  }

  @Test
  void getById_delistedListing_returnsNotFound() throws Exception {
    Listing listing = saveListing(300_000L, 1800, 6, "BUSINESS", ListingStatus.DELISTED);

    mockMvc
        .perform(
            get("/api/v1/marketplace/listings/" + listing.getId())
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void search_withoutAuthentication_isRejected() throws Exception {
    mockMvc.perform(get("/api/v1/marketplace/listings")).andExpect(status().isUnauthorized());
  }
}

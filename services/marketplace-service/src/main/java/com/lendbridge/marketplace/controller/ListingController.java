package com.lendbridge.marketplace.controller;

import com.lendbridge.marketplace.dto.ListingResponse;
import com.lendbridge.marketplace.dto.ListingSearchRequest;
import com.lendbridge.marketplace.service.ListingService;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Any authenticated user may browse — there is no per-lender personalization yet (that needs
 * investment-service's preference sets, which don't exist: PRD E3-S2's "default view = matches
 * for active preferences" is deferred along with the rest of Phase 2). */
@RestController
@RequestMapping("/api/v1/marketplace/listings")
@RequiredArgsConstructor
public class ListingController {

  private final ListingService listingService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ListingResponse>>> search(
      @RequestParam(required = false) Long minAmountKobo,
      @RequestParam(required = false) Long maxAmountKobo,
      @RequestParam(required = false) Integer minRateBps,
      @RequestParam(required = false) Integer maxRateBps,
      @RequestParam(required = false) Integer minTenorMonths,
      @RequestParam(required = false) Integer maxTenorMonths,
      @RequestParam(required = false) String purpose) {
    ListingSearchRequest request =
        ListingSearchRequest.builder()
            .minAmountKobo(minAmountKobo)
            .maxAmountKobo(maxAmountKobo)
            .minRateBps(minRateBps)
            .maxRateBps(maxRateBps)
            .minTenorMonths(minTenorMonths)
            .maxTenorMonths(maxTenorMonths)
            .purpose(purpose)
            .build();
    return ResponseEntity.ok(ApiResponse.success(listingService.search(request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ListingResponse>> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(listingService.getById(id)));
  }
}

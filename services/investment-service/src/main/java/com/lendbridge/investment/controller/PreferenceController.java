package com.lendbridge.investment.controller;

import com.lendbridge.investment.dto.PreferenceRequest;
import com.lendbridge.investment.dto.PreferenceResponse;
import com.lendbridge.investment.security.CurrentUser;
import com.lendbridge.investment.service.PreferenceService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A lender may save several preference sets, editable anytime (PRD E3-S1). */
@RestController
@RequestMapping("/api/v1/investment/preferences")
@RequiredArgsConstructor
public class PreferenceController {

  private final PreferenceService preferenceService;

  @PostMapping
  public ResponseEntity<ApiResponse<PreferenceResponse>> create(
      Authentication authentication, @Valid @RequestBody PreferenceRequest request) {
    PreferenceResponse response = preferenceService.create(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PreferenceResponse>> update(
      Authentication authentication, @PathVariable UUID id, @Valid @RequestBody PreferenceRequest request) {
    PreferenceResponse response = preferenceService.update(CurrentUser.idFrom(authentication), id, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(Authentication authentication, @PathVariable UUID id) {
    preferenceService.delete(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<PreferenceResponse>>> listMine(Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(preferenceService.listMine(CurrentUser.idFrom(authentication))));
  }
}

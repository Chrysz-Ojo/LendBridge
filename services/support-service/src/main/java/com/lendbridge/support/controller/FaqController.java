package com.lendbridge.support.controller;

import com.lendbridge.support.dto.FaqRequest;
import com.lendbridge.support.dto.FaqResponse;
import com.lendbridge.support.service.FaqService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PRD E9-S4: a searchable FAQ page, public to read (see {@code SecurityConfig}), admin-managed. */
@RestController
@RequiredArgsConstructor
public class FaqController {

  private final FaqService faqService;

  @GetMapping("/api/v1/support/faqs")
  public ResponseEntity<ApiResponse<List<FaqResponse>>> search(
      @RequestParam(name = "q", required = false) String query) {
    return ResponseEntity.ok(ApiResponse.success(faqService.findPublished(query)));
  }

  @PostMapping("/api/v1/support/admin/faqs")
  @PreAuthorize("hasRole('SUPPORT')")
  public ResponseEntity<ApiResponse<FaqResponse>> create(@Valid @RequestBody FaqRequest request) {
    return ResponseEntity.ok(ApiResponse.success(faqService.create(request)));
  }

  @PutMapping("/api/v1/support/admin/faqs/{faqId}")
  @PreAuthorize("hasRole('SUPPORT')")
  public ResponseEntity<ApiResponse<FaqResponse>> update(
      @PathVariable UUID faqId, @Valid @RequestBody FaqRequest request) {
    return ResponseEntity.ok(ApiResponse.success(faqService.update(faqId, request)));
  }

  @DeleteMapping("/api/v1/support/admin/faqs/{faqId}")
  @PreAuthorize("hasRole('SUPPORT')")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID faqId) {
    faqService.delete(faqId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}

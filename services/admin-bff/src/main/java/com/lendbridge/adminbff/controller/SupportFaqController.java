package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.FaqRequest;
import com.lendbridge.adminbff.dto.FaqResponse;
import com.lendbridge.adminbff.supportfaq.SupportFaqGateway;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/support/faqs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPPORT')")
public class SupportFaqController {

  private final SupportFaqGateway supportFaqGateway;

  @PostMapping
  public ResponseEntity<ApiResponse<FaqResponse>> create(@Valid @RequestBody FaqRequest request) {
    return ResponseEntity.ok(ApiResponse.success(supportFaqGateway.create(request)));
  }

  @PutMapping("/{faqId}")
  public ResponseEntity<ApiResponse<FaqResponse>> update(
      @PathVariable UUID faqId, @Valid @RequestBody FaqRequest request) {
    return ResponseEntity.ok(ApiResponse.success(supportFaqGateway.update(faqId, request)));
  }

  @DeleteMapping("/{faqId}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID faqId) {
    supportFaqGateway.delete(faqId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}

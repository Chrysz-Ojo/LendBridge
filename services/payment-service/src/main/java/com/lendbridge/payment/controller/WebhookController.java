package com.lendbridge.payment.controller;

import com.lendbridge.payment.service.WebhookService;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public — no JWT, per {@code SecurityConfig}'s explicit permit on this path. A PSP calling this
 * endpoint has no end-user session; security here is entirely {@code provider.verifySignature}
 * (and, for Paystack, its IP allow-list — enforced at the infra/ingress layer, not in this
 * controller). {@code rawBody} is read as raw bytes rather than parsed JSON specifically because
 * signature verification needs the exact bytes the provider signed, byte-for-byte.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

  private final WebhookService webhookService;

  @PostMapping("/{provider}")
  public ResponseEntity<ApiResponse<Void>> receiveWebhook(
      @PathVariable String provider,
      @RequestBody byte[] rawBody,
      @RequestHeader Map<String, String> headers) {
    webhookService.processWebhook(provider, rawBody, headers);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}

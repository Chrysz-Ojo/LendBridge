package com.lendbridge.payment.service;

import java.util.Map;

public interface WebhookService {

  void processWebhook(String providerPathSegment, byte[] rawBody, Map<String, String> headers);
}

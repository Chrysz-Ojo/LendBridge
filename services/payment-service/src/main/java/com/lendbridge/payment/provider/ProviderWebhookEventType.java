package com.lendbridge.payment.provider;

public enum ProviderWebhookEventType {
  CHARGE_SUCCEEDED,
  CHARGE_FAILED,
  TRANSFER_SUCCESSFUL,
  TRANSFER_FAILED,
  UNKNOWN
}

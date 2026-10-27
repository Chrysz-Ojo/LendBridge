package com.lendbridge.payment.model;

public enum ProviderTransactionPurpose {
  CHARGE_INIT,
  CHARGE_VERIFY,
  VIRTUAL_ACCOUNT_CREATE,
  TRANSFER_RECIPIENT_CREATE,
  TRANSFER_INITIATE,
  TRANSFER_OTP_VALIDATE
}

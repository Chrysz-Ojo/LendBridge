package com.lendbridge.notification.model;

public enum NotificationType {
  USER_REGISTERED(true),
  OTP(true),
  KYC_VERIFIED(true),
  KYC_REJECTED(true),
  LOAN_LISTED(true),
  OFFER_RECEIVED(true),
  LOAN_FUNDED(true),
  LOAN_DISBURSED(true),
  REPAYMENT_DUE(true),
  REPAYMENT_OVERDUE(true),
  REPAYMENT_RECEIVED(true),
  LOAN_REPAID(true),
  PAYMENT_FAILED(true),
  SUPPORT_REPLY(true),
  MARKETING(false);

  private final boolean transactional;

  NotificationType(boolean transactional) {
    this.transactional = transactional;
  }

  /** Transactional messages always send; non-transactional ones need marketing opt-in (PRD E9-S3). */
  public boolean isTransactional() {
    return transactional;
  }
}

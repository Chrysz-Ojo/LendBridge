package com.lendbridge.wallet.outbox;

public final class KafkaTopics {

  public static final String WALLET_CREATED = "wallet.created";
  public static final String WALLET_HOLD_CREATED = "wallet.hold.created";
  public static final String WALLET_HOLD_RELEASED = "wallet.hold.released";
  public static final String WALLET_BANK_ACCOUNT_VERIFIED = "wallet.bank_account.verified";
  public static final String WALLET_HOLDS_CAPTURE_ASSEMBLED = "wallet.holds.capture-assembled";
  public static final String WALLET_HOLDS_RESTORED = "wallet.holds.restored";
  public static final String WALLET_BORROWER_ACCOUNT_ASSEMBLED = "wallet.borrower-account.assembled";
  public static final String WALLET_REPAYMENT_ACCOUNT_CONFIRMED = "wallet.repayment-account.confirmed";

  private KafkaTopics() {}
}

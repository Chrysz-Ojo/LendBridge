package com.lendbridge.wallet.outbox;

public final class KafkaTopics {

  public static final String WALLET_CREATED = "wallet.created";
  public static final String WALLET_HOLD_CREATED = "wallet.hold.created";
  public static final String WALLET_HOLD_RELEASED = "wallet.hold.released";
  public static final String WALLET_BANK_ACCOUNT_VERIFIED = "wallet.bank_account.verified";

  private KafkaTopics() {}
}

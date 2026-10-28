package com.lendbridge.kyc.outbox;

public final class KafkaTopics {

  public static final String KYC_VERIFIED = "kyc.verified";
  public static final String KYC_REJECTED = "kyc.rejected";

  private KafkaTopics() {}
}

package com.lendbridge.payment.outbox;

public final class KafkaTopics {

  public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
  public static final String PAYMENT_FAILED = "payment.failed";

  private KafkaTopics() {}
}

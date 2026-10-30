package com.lendbridge.investment.outbox;

public final class KafkaTopics {

  public static final String INVESTMENT_COMMITTED = "investment.committed";
  public static final String INVESTMENT_CANCELLED = "investment.cancelled";

  private KafkaTopics() {}
}

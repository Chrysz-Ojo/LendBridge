package com.lendbridge.loan.outbox;

public final class KafkaTopics {

  public static final String LOAN_SUBMITTED = "loan.submitted";
  public static final String LOAN_LISTED = "loan.listed";
  public static final String LOAN_DELISTED = "loan.delisted";

  private KafkaTopics() {}
}

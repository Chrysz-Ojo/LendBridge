package com.lendbridge.investment.outbox;

public final class KafkaTopics {

  public static final String INVESTMENT_COMMITTED = "investment.committed";
  public static final String INVESTMENT_CANCELLED = "investment.cancelled";
  public static final String INVESTMENT_HOLDS_CAPTURE_REQUESTED = "investment.holds.capture-requested";
  public static final String INVESTMENT_DISBURSEMENT_READY = "investment.disbursement.ready";
  public static final String INVESTMENT_DISBURSEMENT_COMPENSATE = "investment.disbursement.compensate";
  public static final String INVESTMENT_REPAYMENT_READY = "investment.repayment.ready";
  public static final String INVESTMENT_REPAYMENT_FAILED = "investment.repayment.failed";

  private KafkaTopics() {}
}

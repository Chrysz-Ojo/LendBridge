package com.lendbridge.loan.outbox;

public final class KafkaTopics {

  public static final String LOAN_SUBMITTED = "loan.submitted";
  public static final String LOAN_LISTED = "loan.listed";
  public static final String LOAN_DELISTED = "loan.delisted";
  public static final String LOAN_AGREEMENT_SIGNED = "loan.agreement.signed";
  public static final String LOAN_DISBURSED = "loan.disbursed";
  public static final String LOAN_REPAYMENT_REQUESTED = "loan.repayment.requested";
  public static final String LOAN_REPAID = "loan.repaid";

  private KafkaTopics() {}
}

package com.lendbridge.ledger.outbox;

public final class KafkaTopics {

  public static final String LEDGER_POSTED = "ledger.posted";
  public static final String LEDGER_LOAN_DISBURSEMENT_COMPLETED = "ledger.loan-disbursement.completed";
  public static final String LEDGER_LOAN_DISBURSEMENT_FAILED = "ledger.loan-disbursement.failed";
  public static final String LEDGER_LOAN_REPAYMENT_COMPLETED = "ledger.loan-repayment.completed";

  private KafkaTopics() {}
}

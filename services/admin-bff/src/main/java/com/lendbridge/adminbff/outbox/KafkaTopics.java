package com.lendbridge.adminbff.outbox;

public final class KafkaTopics {

  public static final String ADMIN_ACTION_APPROVED = "admin.action.approved";
  public static final String ADMIN_ACTION_REJECTED = "admin.action.rejected";

  private KafkaTopics() {}
}

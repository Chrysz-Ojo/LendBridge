package com.lendbridge.starter.outbox;

public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED
}

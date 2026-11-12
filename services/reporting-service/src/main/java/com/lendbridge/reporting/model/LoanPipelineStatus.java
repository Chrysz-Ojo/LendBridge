package com.lendbridge.reporting.model;

/** One value per loan-lifecycle topic this service consumes — a local copy of the lifecycle stage
 * carried on the event, not a dependency on loan-service's own {@code LoanStatus} enum. */
public enum LoanPipelineStatus {
  SUBMITTED,
  LISTED,
  DELISTED
}

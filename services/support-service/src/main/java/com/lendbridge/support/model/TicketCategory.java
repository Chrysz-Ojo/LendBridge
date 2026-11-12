package com.lendbridge.support.model;

/** PRD E9-S2: "category" from a list — mirrors {@code loan-service}'s {@code LoanPurpose} pattern
 * exactly (an enum with a trailing {@code OTHER}, paired with {@link Ticket#getCategoryDetail()}
 * free text, no conditional "required if OTHER" validation). Also reused by {@link Faq} so tickets
 * and FAQs share one topic taxonomy. */
public enum TicketCategory {
  ACCOUNT,
  LOAN,
  PAYMENT,
  KYC,
  TECHNICAL,
  OTHER
}

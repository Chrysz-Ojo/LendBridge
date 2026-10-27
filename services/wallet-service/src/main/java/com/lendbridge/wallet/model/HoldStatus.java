package com.lendbridge.wallet.model;

/** No {@code CONVERTED} state yet — nothing produces it until Loan's funding saga exists; adding
 * it then is a trivial Liquibase change since this is stored as {@code varchar}, not a native
 * Postgres enum type. */
public enum HoldStatus {
  ACTIVE,
  RELEASED
}

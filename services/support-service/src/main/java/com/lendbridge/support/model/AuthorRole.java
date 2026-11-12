package com.lendbridge.support.model;

/** Distinguishes a ticket thread's two kinds of participants — never resolved against
 * authentication-service's {@code Role} enum (a user could hold the {@code SUPPORT} role and still
 * be a plain ticket owner elsewhere), so this is intentionally a separate, narrower enum. */
public enum AuthorRole {
  USER,
  STAFF
}

package com.lendbridge.authentication.exception;

import com.lendbridge.starter.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Distinct from {@link LockedAccountException}: a locked PIN blocks PIN-gated actions only,
 * never login (E12-S4). */
public class PinLockedException extends ApiException {
  public PinLockedException(String message) {
    super(HttpStatus.LOCKED, "PIN_LOCKED", message);
  }
}

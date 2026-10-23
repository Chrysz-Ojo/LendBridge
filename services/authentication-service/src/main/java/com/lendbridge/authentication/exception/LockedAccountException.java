package com.lendbridge.authentication.exception;

import org.springframework.http.HttpStatus;

public class LockedAccountException extends ApiException {
  public LockedAccountException(String message) {
    super(HttpStatus.LOCKED, "ACCOUNT_LOCKED", message);
  }
}

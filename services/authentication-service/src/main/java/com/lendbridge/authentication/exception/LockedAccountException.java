package com.lendbridge.authentication.exception;

import com.lendbridge.starter.exception.ApiException;
import org.springframework.http.HttpStatus;

public class LockedAccountException extends ApiException {
  public LockedAccountException(String message) {
    super(HttpStatus.LOCKED, "ACCOUNT_LOCKED", message);
  }
}

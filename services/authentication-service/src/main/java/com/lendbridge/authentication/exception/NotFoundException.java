package com.lendbridge.authentication.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
  public NotFoundException(String errorCode, String message) {
    super(HttpStatus.NOT_FOUND, errorCode, message);
  }
}

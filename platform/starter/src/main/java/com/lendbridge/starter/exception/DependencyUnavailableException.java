package com.lendbridge.starter.exception;

import org.springframework.http.HttpStatus;

public class DependencyUnavailableException extends ApiException {
  public DependencyUnavailableException(String errorCode, String message) {
    super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
  }
}

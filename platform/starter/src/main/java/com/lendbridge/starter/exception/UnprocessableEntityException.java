package com.lendbridge.starter.exception;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends ApiException {
  public UnprocessableEntityException(String errorCode, String message) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
  }
}

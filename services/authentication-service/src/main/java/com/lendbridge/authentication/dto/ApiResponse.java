package com.lendbridge.authentication.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Generic success-response envelope. Error responses are unaffected and keep using RFC 9457
 * {@code ProblemDetail} via {@link com.lendbridge.authentication.exception.GlobalExceptionHandler}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

  private boolean success;
  private T data;
  private String message;
  private Instant timestamp;

  public static <T> ApiResponse<T> success(T data) {
    return success(data, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder()
        .success(true)
        .data(data)
        .message(message)
        .timestamp(Instant.now())
        .build();
  }
}

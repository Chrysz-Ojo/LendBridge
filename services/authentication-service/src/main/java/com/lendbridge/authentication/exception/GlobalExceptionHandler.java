package com.lendbridge.authentication.exception;

import com.lendbridge.authentication.config.CorrelationIdFilter;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Minimal RFC 9457 Problem Details mapping; candidate for extraction into platform-starter. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApiException(ApiException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setTitle(ex.getErrorCode());
    problem.setProperty("errorCode", ex.getErrorCode());
    addCorrelationId(problem);
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setTitle("VALIDATION_FAILED");
    problem.setProperty("errorCode", "VALIDATION_FAILED");
    addCorrelationId(problem);
    return problem;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setTitle("VALIDATION_FAILED");
    problem.setProperty("errorCode", "VALIDATION_FAILED");
    addCorrelationId(problem);
    return problem;
  }

  @ExceptionHandler({BadCredentialsException.class})
  public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    problem.setTitle("INVALID_CREDENTIALS");
    problem.setProperty("errorCode", "INVALID_CREDENTIALS");
    addCorrelationId(problem);
    return problem;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access is denied");
    problem.setTitle("ACCESS_DENIED");
    problem.setProperty("errorCode", "ACCESS_DENIED");
    addCorrelationId(problem);
    return problem;
  }

  private void addCorrelationId(ProblemDetail problem) {
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
  }
}

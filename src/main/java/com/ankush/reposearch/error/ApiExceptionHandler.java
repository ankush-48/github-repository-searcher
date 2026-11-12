package com.ankush.reposearch.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private final Clock clock;

  public ApiExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  public record ErrorBody(
      Instant timestamp,
      int status,
      String code,
      String message,
      String path,
      List<String> details) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> handleApi(ApiException exception, HttpServletRequest request) {
    var response =
        body(exception.status(), exception.code(), exception.getMessage(), request, List.of());
    if (exception.retryAfter() == null) return response;
    return ResponseEntity.status(exception.status())
        .header(HttpHeaders.RETRY_AFTER, exception.retryAfter().toString())
        .body(response.getBody());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    var details =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .toList();
    return body(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<ErrorBody> handleInvalid(Exception exception, HttpServletRequest request) {
    return body(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Malformed JSON, invalid parameter type or out-of-range parameter",
        request,
        List.of());
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ErrorBody> handleDatabase(
      DataAccessException exception, HttpServletRequest request) {
    log.error("Database operation failed: {}", exception.getClass().getSimpleName());
    return body(
        HttpStatus.SERVICE_UNAVAILABLE,
        "DATABASE_UNAVAILABLE",
        "The database is temporarily unavailable",
        request,
        List.of());
  }

  @ExceptionHandler({
    NoResourceFoundException.class,
    HttpRequestMethodNotSupportedException.class,
    HttpMediaTypeNotSupportedException.class,
    HttpMediaTypeNotAcceptableException.class
  })
  ResponseEntity<ErrorBody> handleHttp(Exception exception, HttpServletRequest request) {
    var error = (ErrorResponse) exception;
    var status = HttpStatus.valueOf(error.getStatusCode().value());
    var response = body(status, "HTTP_ERROR", status.getReasonPhrase(), request, List.of());
    return ResponseEntity.status(status).headers(error.getHeaders()).body(response.getBody());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Unexpected request failure: {}", exception.getClass().getSimpleName());
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred",
        request,
        List.of());
  }

  private ResponseEntity<ErrorBody> body(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest request,
      List<String> details) {
    return ResponseEntity.status(status)
        .body(
            new ErrorBody(
                clock.instant(), status.value(), code, message, request.getRequestURI(), details));
  }
}

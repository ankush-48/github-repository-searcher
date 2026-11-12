package com.ankush.reposearch.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final Long retryAfter;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, null);
  }

  public ApiException(HttpStatus status, String code, String message, Long retryAfter) {
    super(message);
    this.status = status;
    this.code = code;
    this.retryAfter = retryAfter;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Long retryAfter() {
    return retryAfter;
  }
}

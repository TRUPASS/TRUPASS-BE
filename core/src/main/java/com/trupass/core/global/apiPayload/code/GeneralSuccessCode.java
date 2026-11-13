package com.trupass.core.global.apiPayload.code;

import org.springframework.http.HttpStatus;

public enum GeneralSuccessCode implements BaseSuccessCode {
  OK(HttpStatus.OK, "COMMON200", "성공했습니다."),
  CREATED(HttpStatus.CREATED, "COMMON201", "생성했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  GeneralSuccessCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  @Override
  public HttpStatus getStatus() {
    return status;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}

package com.trupass.core.domain.user.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements BaseErrorCode {
  INVALID_BIRTH_YEAR(HttpStatus.BAD_REQUEST, "USER400_1", "출생 연도가 올바르지 않습니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_1", "사용자를 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  UserErrorCode(HttpStatus status, String code, String message) {
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

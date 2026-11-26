package com.trupass.core.domain.user.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements BaseErrorCode {
  INVALID_BIRTH_YEAR(HttpStatus.BAD_REQUEST, "USER400_1", "출생 연도가 올바르지 않습니다."),
  /** 탈퇴한 회원의 access token으로 온 요청. 401이라 FE는 토큰 재발급을 시도하고, refresh token도 폐기돼 있어 로그아웃된다. */
  WITHDRAWN_USER(HttpStatus.UNAUTHORIZED, "USER401_1", "탈퇴한 계정입니다."),
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

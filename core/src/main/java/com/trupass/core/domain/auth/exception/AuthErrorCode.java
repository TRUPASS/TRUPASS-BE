package com.trupass.core.domain.auth.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 로그인·토큰 에러. access token 자체가 없거나 틀린 경우는 {@code COMMON401}/{@code COMMON401_1}이다. */
public enum AuthErrorCode implements BaseErrorCode {
  INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_1", "Google 인증 정보가 올바르지 않습니다."),
  GOOGLE_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH401_2", "Google 계정 이메일이 인증되지 않았습니다."),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_3", "refresh token이 올바르지 않거나 만료됐습니다."),
  GOOGLE_LOGIN_NOT_CONFIGURED(
      HttpStatus.INTERNAL_SERVER_ERROR, "AUTH500_1", "Google 로그인 설정(client ID)이 없습니다."),
  GOOGLE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AUTH502_1", "Google 인증 서버에 연결하지 못했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  AuthErrorCode(HttpStatus status, String code, String message) {
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

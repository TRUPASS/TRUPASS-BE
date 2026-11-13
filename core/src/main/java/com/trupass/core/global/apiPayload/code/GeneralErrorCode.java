package com.trupass.core.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/** 특정 도메인에 속하지 않는 공통 에러. */
public enum GeneralErrorCode implements BaseErrorCode {
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON400_1", "입력값이 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "COMMON401_1", "access token이 올바르지 않거나 만료됐습니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "권한이 없습니다."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405", "지원하지 않는 HTTP 메서드입니다."),
  CONFLICT(HttpStatus.CONFLICT, "COMMON409", "요청이 현재 상태와 충돌합니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류입니다."),
  NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "COMMON501", "아직 구현되지 않은 기능입니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  GeneralErrorCode(HttpStatus status, String code, String message) {
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

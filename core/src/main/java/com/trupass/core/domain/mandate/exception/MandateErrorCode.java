package com.trupass.core.domain.mandate.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 사용자 서명(Mandate) 검증·보관 에러. */
public enum MandateErrorCode implements BaseErrorCode {
  INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "MANDATE4011", "서명이 올바르지 않습니다."),
  MANDATE_EXPIRED(HttpStatus.GONE, "MANDATE4101", "서명 유효 기간이 지났습니다."),
  AUDIT_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MANDATE5001", "서명 원본을 보관하지 못했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  MandateErrorCode(HttpStatus status, String code, String message) {
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

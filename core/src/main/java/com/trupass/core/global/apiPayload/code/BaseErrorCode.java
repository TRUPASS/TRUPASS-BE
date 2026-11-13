package com.trupass.core.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/** 에러 코드 공통 인터페이스. 도메인마다 {@code XxxErrorCode} enum으로 구현한다. */
public interface BaseErrorCode {

  HttpStatus getStatus();

  /** 예: {@code COMMON400}, {@code PAYMENT4001}, {@code MERCHANT4091}. */
  String getCode();

  String getMessage();
}

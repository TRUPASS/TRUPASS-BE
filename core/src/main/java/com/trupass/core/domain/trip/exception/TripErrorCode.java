package com.trupass.core.domain.trip.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 여행 요청·패키지 조합 에러. */
public enum TripErrorCode implements BaseErrorCode {
  AGENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "TRIP503_1", "AI 에이전트에 연결할 수 없습니다."),
  AGENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "TRIP504_1", "AI 에이전트 응답이 늦어지고 있습니다."),
  INVALID_PACKAGE(HttpStatus.BAD_GATEWAY, "TRIP502_1", "AI 에이전트가 올바르지 않은 패키지를 돌려줬습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  TripErrorCode(HttpStatus status, String code, String message) {
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

package com.trupass.core.domain.payment.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 사용자 결제 에러. */
public enum PaymentErrorCode implements BaseErrorCode {
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT4041", "결제를 찾을 수 없습니다."),
  PAYMENT_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "PAYMENT4221", "결제가 승인되지 않았습니다."),
  CANCEL_AMOUNT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "PAYMENT4222", "취소 금액이 남은 결제 금액보다 큽니다."),
  INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "PAYMENT4011", "웹훅 서명이 올바르지 않습니다."),
  PG_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "PAYMENT5021", "결제사 서버에 오류가 발생했습니다."),
  PG_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT5041", "결제사 응답 시간이 초과됐습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  PaymentErrorCode(HttpStatus status, String code, String message) {
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

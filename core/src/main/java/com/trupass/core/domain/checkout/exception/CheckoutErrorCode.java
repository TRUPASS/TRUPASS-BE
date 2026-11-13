package com.trupass.core.domain.checkout.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 주문 진행 에러. */
public enum CheckoutErrorCode implements BaseErrorCode {
  CHECKOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHECKOUT4041", "주문을 찾을 수 없습니다."),
  INVALID_STATUS(HttpStatus.CONFLICT, "CHECKOUT4091", "현재 주문 상태에서 할 수 없는 요청입니다."),
  AMOUNT_MISMATCH(HttpStatus.CONFLICT, "CHECKOUT4092", "확정된 금액과 요청 금액이 다릅니다."),
  NEEDS_MANUAL_REVIEW(
      HttpStatus.INTERNAL_SERVER_ERROR, "CHECKOUT5001", "자동 되돌리기에 실패해 수동 확인이 필요합니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  CheckoutErrorCode(HttpStatus status, String code, String message) {
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

package com.trupass.core.domain.merchant.exception;

import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 판매자 호출 실패 사유. 주문 진행(checkout)이 재시도/되돌리기 여부를 판단하는 근거다. */
public enum MerchantErrorCode implements BaseErrorCode {
  OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "MERCHANT4041", "상품을 찾을 수 없습니다."),
  SOLD_OUT(HttpStatus.CONFLICT, "MERCHANT4091", "품절된 상품입니다."),
  HOLD_EXPIRED(HttpStatus.GONE, "MERCHANT4101", "자리 잡기(hold)가 만료됐습니다."),
  REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "MERCHANT4221", "판매자가 요청을 거절했습니다."),
  UNAVAILABLE(HttpStatus.BAD_GATEWAY, "MERCHANT5021", "판매자 서버에 오류가 발생했습니다."),
  TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "MERCHANT5041", "판매자 응답 시간이 초과됐습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  MerchantErrorCode(HttpStatus status, String code, String message) {
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

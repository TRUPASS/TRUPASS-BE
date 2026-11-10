package com.trupass.core.domain.merchant;

/** 판매자 호출 실패 사유. Saga가 재시도/보상 여부를 판단하는 근거다. */
public enum MerchantFailureReason {
  SOLD_OUT,
  HOLD_EXPIRED,
  TIMEOUT,
  UNAVAILABLE,
  REJECTED
}

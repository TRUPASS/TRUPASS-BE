package com.trupass.core.domain.payment;

import com.trupass.core.domain.checkout.IdempotencyKey;
import com.trupass.core.domain.common.Money;

/**
 * 결제 취소 요청.
 *
 * @param cancelAmount null이면 전체 취소, 값이 있으면 그 금액만큼 부분 취소.
 */
public record PaymentCancelRequest(
    PaymentId paymentId, Money cancelAmount, String reason, IdempotencyKey idempotencyKey) {

  public boolean isPartial() {
    return cancelAmount != null;
  }
}

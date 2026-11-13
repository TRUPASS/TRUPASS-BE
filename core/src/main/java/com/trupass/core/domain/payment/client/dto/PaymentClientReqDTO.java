package com.trupass.core.domain.payment.client.dto;

import com.trupass.core.global.common.IdempotencyKey;
import com.trupass.core.global.common.Money;
import java.util.List;
import java.util.Map;

/** {@code PaymentClient}에 넘기는 요청 형식. */
public final class PaymentClientReqDTO {
  private PaymentClientReqDTO() {}

  /**
   * 사용자 → Trupass 결제 승인(COLLECT). 주문당 1건이다.
   *
   * @param paymentToken FE 결제창이 돌려준 PG 측 인증 토큰(토스의 paymentKey 등).
   */
  public record Approve(
      String checkoutId, Money amount, String paymentToken, IdempotencyKey idempotencyKey) {}

  /**
   * 결제 취소.
   *
   * @param cancelAmount null이면 전체 취소, 값이 있으면 그 금액만큼 부분 취소.
   */
  public record Cancel(
      String paymentId, Money cancelAmount, String reason, IdempotencyKey idempotencyKey) {

    public boolean isPartial() {
      return cancelAmount != null;
    }
  }

  /** PG 웹훅 원문. 서명 검증은 client가 하므로 본문 바이트를 손대지 않고 그대로 넘긴다. */
  public record Webhook(Map<String, List<String>> headers, byte[] body) {
    public Webhook {
      headers = Map.copyOf(headers);
      body = body.clone();
    }

    @Override
    public byte[] body() {
      return body.clone();
    }
  }
}

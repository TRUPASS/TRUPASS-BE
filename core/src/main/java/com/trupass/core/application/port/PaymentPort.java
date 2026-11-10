package com.trupass.core.application.port;

import com.trupass.core.domain.payment.CollectRequest;
import com.trupass.core.domain.payment.Payment;
import com.trupass.core.domain.payment.PaymentCancelRequest;
import com.trupass.core.domain.payment.PaymentCancellation;
import com.trupass.core.domain.payment.PaymentEvent;
import com.trupass.core.domain.payment.RawWebhook;

/**
 * 사용자 → Trupass 결제를 다루는 포트.
 *
 * <p>어댑터: {@code trupass.adapters.payment=mock}(인프로세스 Mock PG, 타임아웃·실패 주입 가능) | {@code
 * toss}(토스페이먼츠).
 */
public interface PaymentPort {

  /** 사용자 결제를 승인한다(Saga COLLECT). 체크아웃당 1건이다. */
  Payment collect(CollectRequest request);

  /**
   * 결제를 취소한다. {@link PaymentCancelRequest#cancelAmount()}가 null이면 전체 취소, 값이 있으면 부분 취소다. Saga 보상에서는
   * 이미 정산된 판매자분을 제외하고 부분 취소한다.
   */
  PaymentCancellation cancel(PaymentCancelRequest request);

  /** PG 웹훅을 검증하고 도메인 이벤트로 해석한다. 서명이 맞지 않으면 예외를 던진다. */
  PaymentEvent parseWebhook(RawWebhook webhook);
}

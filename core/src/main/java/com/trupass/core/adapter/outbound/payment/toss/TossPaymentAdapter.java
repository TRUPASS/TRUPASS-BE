package com.trupass.core.adapter.outbound.payment.toss;

import com.trupass.core.application.port.PaymentPort;
import com.trupass.core.domain.payment.CollectRequest;
import com.trupass.core.domain.payment.Payment;
import com.trupass.core.domain.payment.PaymentCancelRequest;
import com.trupass.core.domain.payment.PaymentCancellation;
import com.trupass.core.domain.payment.PaymentEvent;
import com.trupass.core.domain.payment.RawWebhook;

/** 토스페이먼츠 어댑터 자리. 키는 환경 변수 {@code TOSS_SECRET_KEY}로 받는다. */
public class TossPaymentAdapter implements PaymentPort {

  @Override
  public Payment collect(CollectRequest request) {
    throw new UnsupportedOperationException("토스페이먼츠 연동 예정: 결제 승인");
  }

  @Override
  public PaymentCancellation cancel(PaymentCancelRequest request) {
    throw new UnsupportedOperationException("토스페이먼츠 연동 예정: 부분/전체 취소");
  }

  @Override
  public PaymentEvent parseWebhook(RawWebhook webhook) {
    throw new UnsupportedOperationException("토스페이먼츠 연동 예정: 웹훅 해석");
  }
}

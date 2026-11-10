package com.trupass.core.adapter.outbound.payment.mockpg;

import com.trupass.core.application.port.PaymentPort;
import com.trupass.core.domain.payment.CollectRequest;
import com.trupass.core.domain.payment.Payment;
import com.trupass.core.domain.payment.PaymentCancelRequest;
import com.trupass.core.domain.payment.PaymentCancellation;
import com.trupass.core.domain.payment.PaymentEvent;
import com.trupass.core.domain.payment.RawWebhook;

/** 인프로세스 Mock PG. 담당자가 타임아웃·실패 주입이 가능한 형태로 구현한다. */
public class MockPgPaymentAdapter implements PaymentPort {

  @Override
  public Payment collect(CollectRequest request) {
    throw new UnsupportedOperationException("담당자 구현(Mock PG): 결제 승인");
  }

  @Override
  public PaymentCancellation cancel(PaymentCancelRequest request) {
    throw new UnsupportedOperationException("담당자 구현(Mock PG): 부분/전체 취소");
  }

  @Override
  public PaymentEvent parseWebhook(RawWebhook webhook) {
    throw new UnsupportedOperationException("담당자 구현(Mock PG): 웹훅 해석");
  }
}

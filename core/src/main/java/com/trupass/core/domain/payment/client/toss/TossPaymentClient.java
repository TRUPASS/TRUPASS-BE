package com.trupass.core.domain.payment.client.toss;

import com.trupass.core.domain.payment.client.PaymentClient;
import com.trupass.core.domain.payment.client.dto.PaymentClientReqDTO;
import com.trupass.core.domain.payment.client.dto.PaymentClientResDTO;

/** 토스페이먼츠 구현체 자리. 키는 환경 변수 {@code TOSS_SECRET_KEY}로 받는다. */
public class TossPaymentClient implements PaymentClient {

  @Override
  public PaymentClientResDTO.Approval approve(PaymentClientReqDTO.Approve request) {
    throw new UnsupportedOperationException("토스페이먼츠 연동 예정: 결제 승인");
  }

  @Override
  public PaymentClientResDTO.Cancellation cancel(PaymentClientReqDTO.Cancel request) {
    throw new UnsupportedOperationException("토스페이먼츠 연동 예정: 전체/부분 취소");
  }

  @Override
  public PaymentClientResDTO.WebhookEvent parseWebhook(PaymentClientReqDTO.Webhook webhook) {
    throw new UnsupportedOperationException("토스페이먼츠 연동 예정: 웹훅 해석");
  }
}

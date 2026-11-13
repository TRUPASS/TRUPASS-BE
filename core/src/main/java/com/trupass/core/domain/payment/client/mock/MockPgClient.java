package com.trupass.core.domain.payment.client.mock;

import com.trupass.core.domain.payment.client.PaymentClient;
import com.trupass.core.domain.payment.client.dto.PaymentClientReqDTO;
import com.trupass.core.domain.payment.client.dto.PaymentClientResDTO;

/** 인프로세스 Mock PG. 담당자가 타임아웃·실패 주입이 가능한 형태로 구현한다. */
public class MockPgClient implements PaymentClient {

  @Override
  public PaymentClientResDTO.Approval approve(PaymentClientReqDTO.Approve request) {
    throw new UnsupportedOperationException("담당자 구현(Mock PG): 결제 승인");
  }

  @Override
  public PaymentClientResDTO.Cancellation cancel(PaymentClientReqDTO.Cancel request) {
    throw new UnsupportedOperationException("담당자 구현(Mock PG): 전체/부분 취소");
  }

  @Override
  public PaymentClientResDTO.WebhookEvent parseWebhook(PaymentClientReqDTO.Webhook webhook) {
    throw new UnsupportedOperationException("담당자 구현(Mock PG): 웹훅 해석");
  }
}

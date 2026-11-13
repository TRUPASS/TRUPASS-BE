package com.trupass.core.domain.payment.client;

import com.trupass.core.domain.payment.client.dto.PaymentClientReqDTO;
import com.trupass.core.domain.payment.client.dto.PaymentClientResDTO;

/**
 * 사용자 → Trupass 결제(PG) 연동. service는 이 인터페이스만 안다.
 *
 * <p>구현체: {@code trupass.clients.payment=mock}(인프로세스 Mock PG, 타임아웃·실패 주입 가능) | {@code
 * toss}(토스페이먼츠). 연결은 {@code global/config/ClientConfig}에서만 한다.
 */
public interface PaymentClient {

  /** 사용자 결제를 승인한다(COLLECT). 주문당 1건이다. */
  PaymentClientResDTO.Approval approve(PaymentClientReqDTO.Approve request);

  /** 결제를 전체 또는 부분 취소한다. 되돌리기에서는 이미 지급된 판매자 몫을 빼고 부분 취소한다. */
  PaymentClientResDTO.Cancellation cancel(PaymentClientReqDTO.Cancel request);

  /** PG 웹훅 서명을 검증하고 이벤트로 해석한다. 서명이 맞지 않으면 INVALID_WEBHOOK_SIGNATURE로 실패한다. */
  PaymentClientResDTO.WebhookEvent parseWebhook(PaymentClientReqDTO.Webhook webhook);
}

package com.trupass.core.domain.payment.service;

import com.trupass.core.domain.payment.client.PaymentClient;
import com.trupass.core.domain.payment.client.dto.PaymentClientReqDTO;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 사용자 결제: 승인·부분 취소·웹훅. PG 호출은 {@link PaymentClient}로만 한다. */
@Service
public class PaymentService {
  private final PaymentClient paymentClient;

  public PaymentService(PaymentClient paymentClient) {
    this.paymentClient = paymentClient;
  }

  /** 웹훅 원문을 검증·해석한다. 결제 상태 반영은 Payment 엔티티를 만들 때 이어서 구현한다. */
  public void handleWebhook(Map<String, List<String>> headers, byte[] body) {
    paymentClient.parseWebhook(new PaymentClientReqDTO.Webhook(headers, body));
  }
}

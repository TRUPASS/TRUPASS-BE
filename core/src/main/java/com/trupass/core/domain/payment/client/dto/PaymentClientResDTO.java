package com.trupass.core.domain.payment.client.dto;

import com.trupass.core.global.common.Money;
import java.time.Instant;

/** {@code PaymentClient}가 돌려주는 응답 형식. */
public final class PaymentClientResDTO {
  private PaymentClientResDTO() {}

  /**
   * @param paymentId PG가 발급한 결제 식별자(토스의 paymentKey 등).
   */
  public record Approval(String paymentId, String checkoutId, Money amount, Instant approvedAt) {}

  public record Cancellation(
      String paymentId, Money cancelledAmount, Money remainingAmount, Instant cancelledAt) {}

  public enum WebhookEventType {
    APPROVED,
    CANCELLED,
    PARTIALLY_CANCELLED,
    FAILED,
    UNKNOWN
  }

  public record WebhookEvent(String paymentId, WebhookEventType type, Instant occurredAt) {}
}

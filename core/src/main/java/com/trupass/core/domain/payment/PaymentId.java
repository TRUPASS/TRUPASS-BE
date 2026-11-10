package com.trupass.core.domain.payment;

/** PG가 발급한 결제 식별자(토스의 paymentKey 등). */
public record PaymentId(String value) {
  @Override
  public String toString() {
    return value;
  }
}

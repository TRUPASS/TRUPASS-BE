package com.trupass.core.global.web;

/** 주문 번호(checkout_id)를 로그(MDC)와 HTTP 헤더로 전파할 때 쓰는 이름. */
public final class CheckoutMdc {
  public static final String KEY = "checkout_id";
  public static final String HEADER = "X-Checkout-Id";

  private CheckoutMdc() {}
}

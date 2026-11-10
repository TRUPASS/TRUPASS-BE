package com.trupass.core.domain.common;

import java.util.Objects;

/** 금액. 부동소수점 오차를 피하려고 통화의 최소 단위 정수(long)로만 다룬다. KRW는 원 단위다. */
public record Money(long amount, CurrencyCode currency) {
  public Money {
    if (amount < 0) {
      throw new IllegalArgumentException("amount must not be negative: " + amount);
    }
    Objects.requireNonNull(currency, "currency");
  }

  public static Money krw(long amount) {
    return new Money(amount, CurrencyCode.KRW);
  }
}

package com.trupass.core.global.common;

import java.util.Objects;

/**
 * 금액. 부동소수점 오차를 피하려고 통화의 최소 단위 정수(long)로만 다룬다. KRW는 원 단위다.
 *
 * @param currency ISO 4217 통화 코드(대문자 3자리)
 */
public record Money(long amount, String currency) {
  public static final String KRW = "KRW";

  public Money {
    if (amount < 0) {
      throw new IllegalArgumentException("amount must not be negative: " + amount);
    }
    Objects.requireNonNull(currency, "currency");
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("invalid ISO 4217 code: " + currency);
    }
  }

  public static Money krw(long amount) {
    return new Money(amount, KRW);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(amount, other.amount), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(amount, other.amount), currency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "currency mismatch: " + currency + " vs " + other.currency);
    }
  }
}

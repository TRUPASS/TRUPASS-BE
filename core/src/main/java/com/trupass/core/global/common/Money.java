package com.trupass.core.global.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
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

  /**
   * 외부 API가 주는 "보통 단위" 소수 금액(USD {@code 401.40})을 최소 단위 정수({@code 40140})로 바꾼다. 통화가 허용하는 자릿수보다 소수가
   * 길면 반올림하지 않고 {@link ArithmeticException}을 던진다. 금액을 몰래 바꾸지 않기 위해서다.
   */
  public static Money fromDecimal(BigDecimal amount, String currency) {
    Objects.requireNonNull(amount, "amount");
    int digits = fractionDigits(currency);
    long minor =
        amount.setScale(digits, RoundingMode.UNNECESSARY).movePointRight(digits).longValueExact();
    return new Money(minor, currency);
  }

  /** 최소 단위 정수를 보통 단위 소수로 돌려준다. USD {@code 40140} → {@code 401.40}. */
  public BigDecimal toDecimal() {
    return BigDecimal.valueOf(amount, fractionDigits(currency));
  }

  private static int fractionDigits(String currency) {
    int digits = Currency.getInstance(currency).getDefaultFractionDigits();
    if (digits < 0) {
      throw new IllegalArgumentException("currency has no minor unit: " + currency);
    }
    return digits;
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

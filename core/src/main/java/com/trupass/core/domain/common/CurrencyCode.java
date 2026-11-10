package com.trupass.core.domain.common;

/** ISO 4217 통화 코드(대문자 3자리). */
public record CurrencyCode(String value) {
  public static final CurrencyCode KRW = new CurrencyCode("KRW");

  public CurrencyCode {
    if (value == null || !value.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("invalid ISO 4217 code: " + value);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}

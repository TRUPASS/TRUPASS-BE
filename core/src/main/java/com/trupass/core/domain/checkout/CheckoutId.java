package com.trupass.core.domain.checkout;

import java.util.Objects;

/** 체크아웃(=Saga 인스턴스) 식별자. */
public record CheckoutId(String value) {
  public CheckoutId {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public String toString() {
    return value;
  }
}

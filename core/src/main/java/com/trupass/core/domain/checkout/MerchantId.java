package com.trupass.core.domain.checkout;

import java.util.Objects;

/** 판매자 식별자. Mock 판매자는 A(항공)·B(숙소)·C(체험)이다. */
public record MerchantId(String value) {
  /** 사용자 → Trupass 결제(COLLECT)처럼 판매자가 아닌 Trupass 자신이 상대인 단계에 쓴다. */
  public static final MerchantId PLATFORM = new MerchantId("trupass");

  public MerchantId {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public String toString() {
    return value;
  }
}

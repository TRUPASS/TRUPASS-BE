package com.trupass.core.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

  @Test
  void 멱등키는_주문번호_판매자_단계_형식이다() {
    assertThat(IdempotencyKey.of("co_1", "A", "HOLD").value()).isEqualTo("co_1:A:HOLD");
    assertThat(IdempotencyKey.of("co_1", IdempotencyKey.PLATFORM, "COLLECT").value())
        .isEqualTo("co_1:trupass:COLLECT");
  }

  @Test
  void 금액은_음수가_될_수_없고_통화가_같아야_더할_수_있다() {
    assertThat(Money.krw(1_000).plus(Money.krw(500))).isEqualTo(Money.krw(1_500));
    assertThatThrownBy(() -> Money.krw(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.krw(1).plus(new Money(1, "USD")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

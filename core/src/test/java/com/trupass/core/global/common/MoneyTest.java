package com.trupass.core.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 외부 API 금액(소수 문자열)을 최소 단위 정수로 바꾸는 규칙. 금액을 몰래 반올림하지 않는지가 핵심이다. */
class MoneyTest {

  @Test
  void 소수_금액을_통화의_최소_단위_정수로_바꾼다() {
    assertThat(Money.fromDecimal(new BigDecimal("401.40"), "USD"))
        .isEqualTo(new Money(40140, "USD"));
    assertThat(Money.fromDecimal(new BigDecimal("401.4"), "USD"))
        .isEqualTo(new Money(40140, "USD"));
    assertThat(Money.fromDecimal(new BigDecimal("388805"), "KRW")).isEqualTo(Money.krw(388805));
    assertThat(Money.fromDecimal(new BigDecimal("388805.00"), "KRW")).isEqualTo(Money.krw(388805));
    assertThat(Money.fromDecimal(new BigDecimal("12345"), "JPY"))
        .isEqualTo(new Money(12345, "JPY"));
  }

  @Test
  void 통화가_허용하는_자릿수보다_소수가_길면_반올림하지_않고_실패한다() {
    assertThatThrownBy(() -> Money.fromDecimal(new BigDecimal("401.405"), "USD"))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Money.fromDecimal(new BigDecimal("1000.5"), "KRW"))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void 최소_단위_정수를_소수로_돌려준다() {
    assertThat(new Money(40140, "USD").toDecimal().toPlainString()).isEqualTo("401.40");
    assertThat(Money.krw(388805).toDecimal().toPlainString()).isEqualTo("388805");
  }

  @Test
  void 잘못된_통화_코드나_음수는_만들_수_없다() {
    assertThatThrownBy(() -> new Money(100, "usd")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.krw(-1)).isInstanceOf(IllegalArgumentException.class);
    // 형식은 맞지만 ISO 4217에 없는 코드, 최소 단위가 없는 코드(XXX)
    assertThatThrownBy(() -> Money.fromDecimal(BigDecimal.ONE, "ABC"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.fromDecimal(BigDecimal.ONE, "XXX"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 통화가_다르면_더할_수_없고_넘치면_실패한다() {
    assertThat(Money.krw(561960).plus(Money.krw(388805))).isEqualTo(Money.krw(950765));
    assertThatThrownBy(() -> Money.krw(1).plus(new Money(1, "USD")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.krw(Long.MAX_VALUE).plus(Money.krw(1)))
        .isInstanceOf(ArithmeticException.class);
  }
}

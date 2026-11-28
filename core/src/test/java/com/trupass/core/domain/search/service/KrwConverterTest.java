package com.trupass.core.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trupass.core.global.common.Money;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KrwConverterTest {

  @Test
  void 외화를_고정_환율로_원화로_바꾼다() {
    KrwConverter converter = new KrwConverter(Map.of("USD", new BigDecimal("1400")));

    // USD 401.40 × 1400 = 561,960원
    assertThat(converter.toKrw(new Money(40140, "USD"))).isEqualTo(Money.krw(561960));
  }

  @Test
  void 원_미만은_반올림한다() {
    // 0.01달러 × 1450 = 14.5원 → 15원(HALF_UP). HALF_EVEN이면 14원이 된다.
    assertThat(new KrwConverter(Map.of("USD", new BigDecimal("1450"))).toKrw(new Money(1, "USD")))
        .isEqualTo(Money.krw(15));
    // 0.01달러 × 1440 = 14.4원 → 14원
    assertThat(new KrwConverter(Map.of("USD", new BigDecimal("1440"))).toKrw(new Money(1, "USD")))
        .isEqualTo(Money.krw(14));
  }

  @Test
  void 소수_자리가_없는_통화도_바꾼다() {
    KrwConverter converter = new KrwConverter(Map.of("JPY", new BigDecimal("9.2")));

    assertThat(converter.toKrw(new Money(10000, "JPY"))).isEqualTo(Money.krw(92000));
  }

  @Test
  void 원화는_그대로_돌려준다() {
    assertThat(new KrwConverter(Map.of()).toKrw(Money.krw(388805))).isEqualTo(Money.krw(388805));
  }

  @Test
  void 환율이_없는_통화는_짐작하지_않고_실패한다() {
    KrwConverter converter = new KrwConverter(Map.of("USD", new BigDecimal("1400")));

    assertThatThrownBy(() -> converter.toKrw(new Money(100, "EUR")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("EUR");
  }

  @Test
  void 환율이_0_이하면_애플리케이션이_뜨지_않는다() {
    assertThatThrownBy(() -> new KrwConverter(Map.of("USD", BigDecimal.ZERO)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new KrwConverter(Map.of("USD", new BigDecimal("-1"))))
        .isInstanceOf(IllegalStateException.class);
  }
}

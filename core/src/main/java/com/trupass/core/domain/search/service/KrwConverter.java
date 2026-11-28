package com.trupass.core.domain.search.service;

import com.trupass.core.global.common.Money;
import com.trupass.core.global.config.TrupassProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 외화 금액을 원화로 바꾼다. 예산 비교와 정렬에만 쓰고, 원래 통화 금액은 따로 남긴다.
 *
 * <p>환율은 {@code trupass.search.krw-rates}의 고정값이다(통화 1단위당 원). 원 단위 미만은 반올림한다(HALF_UP). 돈 계산이라 리뷰어 2명
 * 규칙 대상이다.
 */
@Component
public class KrwConverter {
  private final Map<String, BigDecimal> rates;

  /** 생성자가 둘이라 Spring이 고를 쪽을 표시한다. 아래 생성자는 테스트용이다. */
  @Autowired
  public KrwConverter(TrupassProperties properties) {
    this(properties.search().krwRates());
  }

  KrwConverter(Map<String, BigDecimal> rates) {
    rates.forEach((currency, rate) -> {
      if (rate == null || rate.signum() <= 0) {
        throw new IllegalStateException("trupass.search.krw-rates." + currency + " must be > 0");
      }
    });
    this.rates = Map.copyOf(rates);
  }

  /** USD 401.40(40140) × 1400 → 561,960원. 환율이 없는 통화면 {@link IllegalStateException}. */
  public Money toKrw(Money money) {
    if (Money.KRW.equals(money.currency())) {
      return money;
    }
    BigDecimal rate = rates.get(money.currency());
    if (rate == null) {
      throw new IllegalStateException("no KRW rate configured for " + money.currency());
    }
    BigDecimal krw = money.toDecimal().multiply(rate).setScale(0, RoundingMode.HALF_UP);
    return Money.krw(krw.longValueExact());
  }
}

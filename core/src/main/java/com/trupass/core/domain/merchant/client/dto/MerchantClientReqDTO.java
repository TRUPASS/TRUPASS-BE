package com.trupass.core.domain.merchant.client.dto;

import com.trupass.core.domain.merchant.entity.enums.OfferCategory;
import com.trupass.core.global.common.IdempotencyKey;
import com.trupass.core.global.common.Money;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** {@code MerchantClient}에 넘기는 요청 형식. */
public final class MerchantClientReqDTO {
  private MerchantClientReqDTO() {}

  /** 상품 검색 조건. 카테고리가 비어 있으면 전체를 찾는다. */
  public record SearchOffers(
      String destination,
      LocalDate startDate,
      LocalDate endDate,
      int travelers,
      Set<OfferCategory> categories) {
    public SearchOffers {
      categories =
          categories == null || categories.isEmpty()
              ? Set.copyOf(EnumSet.allOf(OfferCategory.class))
              : Set.copyOf(categories);
    }
  }

  /** 자리 잡기(HOLD). */
  public record Hold(
      String merchantId, String offerId, int quantity, IdempotencyKey idempotencyKey) {}

  /** Trupass → 판매자 대금 지급(PAY). */
  public record Pay(
      String merchantId, String holdId, Money amount, IdempotencyKey idempotencyKey) {}
}

package com.trupass.core.domain.merchant.client.dto;

import com.trupass.core.domain.merchant.entity.enums.OfferCategory;
import com.trupass.core.global.common.Money;
import java.time.Instant;

/** {@code MerchantClient}가 돌려주는 응답 형식. */
public final class MerchantClientResDTO {
  private MerchantClientResDTO() {}

  /**
   * 판매자가 제시한 상품 1건. 금액은 반드시 상품 ID와 함께 다닌다.
   *
   * @param expiresAt 상품 유효 기한. 없으면 null.
   */
  public record Offer(
      String offerId,
      String merchantId,
      OfferCategory category,
      String title,
      Money price,
      Instant expiresAt) {}

  /** 재고만 잡은 상태. 돈은 아직 움직이지 않았다. {@code expiresAt}이 지나면 판매자가 자동 해제한다. */
  public record Hold(
      String holdId, String merchantId, String offerId, Money amount, Instant expiresAt) {}

  /** Trupass → 판매자 대금 지급 결과. */
  public record Payment(String holdId, Money amount, Instant paidAt) {}

  public record Booking(String bookingId, String holdId, String merchantId, Instant confirmedAt) {}

  /**
   * 예약 취소 결과.
   *
   * @param refundedAmount 판매자가 돌려준 금액. 없으면 null.
   */
  public record Cancellation(String bookingId, Money refundedAmount, Instant cancelledAt) {}
}

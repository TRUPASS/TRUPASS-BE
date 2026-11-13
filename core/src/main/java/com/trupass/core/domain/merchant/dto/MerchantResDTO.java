package com.trupass.core.domain.merchant.dto;

import com.trupass.core.domain.merchant.client.dto.MerchantClientResDTO;
import com.trupass.core.domain.merchant.entity.enums.OfferCategory;
import java.time.Instant;
import java.util.List;

public final class MerchantResDTO {
  private MerchantResDTO() {}

  /** agent의 {@code OfferRef} 스키마와 필드를 맞춘다(JSON은 camelCase). */
  public record Offer(
      String offerId,
      String merchantId,
      OfferCategory category,
      String title,
      long amount,
      String currency,
      Instant expiresAt) {

    public static Offer from(MerchantClientResDTO.Offer offer) {
      return new Offer(
          offer.offerId(),
          offer.merchantId(),
          offer.category(),
          offer.title(),
          offer.price().amount(),
          offer.price().currency(),
          offer.expiresAt());
    }
  }

  public record SearchOffers(List<Offer> offers) {}
}

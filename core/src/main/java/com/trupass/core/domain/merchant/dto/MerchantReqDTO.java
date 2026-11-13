package com.trupass.core.domain.merchant.dto;

import com.trupass.core.domain.merchant.entity.enums.OfferCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

public final class MerchantReqDTO {
  private MerchantReqDTO() {}

  /** agent가 보내는 상품 검색 조건. JSON은 camelCase. */
  public record SearchOffers(
      @NotBlank String destination,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @Min(1) int travelers,
      Set<OfferCategory> categories) {}
}

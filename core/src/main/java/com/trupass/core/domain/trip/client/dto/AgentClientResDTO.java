package com.trupass.core.domain.trip.client.dto;

import java.util.List;

/**
 * {@code AgentClient}가 돌려주는 응답 형식. agent의 {@code TravelPackage} 스키마와 맞춘다.
 *
 * <p>agent는 금액을 만들거나 바꾸지 않는다. 여기 담긴 금액은 참고용이고, 최종 금액은 core가 상품 ID로 판매자에게 다시 확인해 확정한다.
 */
public final class AgentClientResDTO {
  private AgentClientResDTO() {}

  public record OfferRef(
      String offerId,
      String merchantId,
      String category,
      String title,
      long amount,
      String currency) {}

  public record PackageOption(
      String optionId, String title, String summary, List<OfferRef> offers) {}

  /** 패키지 2~3안. */
  public record Compose(String requestSummary, List<PackageOption> options) {}
}

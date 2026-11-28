package com.trupass.core.domain.search.client.dto;

import com.trupass.core.global.common.Money;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검색 client가 돌려주는 offer. 소스와 상관없는 모양이다. 금액은 소스가 준 통화 그대로 두고, 원화 환산은 service가 한다.
 *
 * <p>테스트·샌드박스 데이터라 가격은 실제 판매가가 아니다.
 */
public final class SearchClientResDTO {
  private SearchClientResDTO() {}

  /**
   * 왕복 항공 offer 하나.
   *
   * @param source 어느 소스에서 왔는지(예: {@code DUFFEL_TEST}).
   * @param price 전체 인원 합계.
   * @param expiresAt 이 시각이 지나면 소스에서 offer가 사라진다. 주지 않는 소스면 {@code null}.
   * @param slices 가는 편, 오는 편 순서.
   */
  public record FlightOffer(
      String source,
      String offerId,
      String ownerCode,
      String ownerName,
      Money price,
      Instant expiresAt,
      List<FlightSlice> slices) {}

  /**
   * 편도 하나.
   *
   * @param departingAt 출발 공항 현지 시각(오프셋 없음).
   */
  public record FlightSlice(
      String originAirport,
      String destinationAirport,
      LocalDateTime departingAt,
      LocalDateTime arrivingAt,
      Duration duration,
      List<FlightSegment> segments) {

    /** 경유 횟수. 직항이면 0. */
    public int stops() {
      return Math.max(0, segments.size() - 1);
    }
  }

  /**
   * 실제로 타는 비행기 하나.
   *
   * @param flightNumber 판매 항공사 코드 + 편명(예: {@code NH862}).
   */
  public record FlightSegment(
      String flightNumber,
      String carrierName,
      String originAirport,
      String destinationAirport,
      LocalDateTime departingAt,
      LocalDateTime arrivingAt) {}

  /**
   * 숙소 offer 하나(호텔 + 가장 싼 객실).
   *
   * @param offerId 소스의 객실 요금 id. LiteAPI는 1,000자가 넘는다.
   * @param stars 성급 1–5. 모르면 {@code null}.
   * @param reviewScore 리뷰 점수 0–10. 모르면 {@code null}.
   * @param boardType 식사 코드({@code RO} 객실만, {@code BI} 조식 포함 등).
   * @param taxesIncluded 세금·수수료가 {@code price}에 모두 들어 있는지.
   * @param price 숙박 전체 기간, 전체 인원 합계.
   */
  public record StayOffer(
      String source,
      String hotelId,
      String offerId,
      String name,
      Integer stars,
      Double reviewScore,
      Integer reviewCount,
      String address,
      Double latitude,
      Double longitude,
      String roomName,
      String boardType,
      String boardName,
      boolean refundable,
      boolean taxesIncluded,
      Money price) {}
}

package com.trupass.core.domain.search.client.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** 검색 client에 넘기는 조건. 소스마다 다른 요청 형식은 구현체가 바꾼다. */
public final class SearchClientReqDTO {
  private SearchClientReqDTO() {}

  /**
   * 왕복 항공 검색 조건.
   *
   * @param originCityCode IATA 도시 코드(예: {@code SEL}). 공항 코드를 넣어도 된다.
   * @param departureTimeFrom 가는 편 출발 시각 범위 시작. {@code null}이면 제한 없음.
   * @param maxConnections 편도당 최대 경유 횟수.
   */
  public record Flight(
      String originCityCode,
      String destinationCityCode,
      LocalDate departureDate,
      LocalDate returnDate,
      int adults,
      LocalTime departureTimeFrom,
      LocalTime departureTimeTo,
      int maxConnections) {}

  /**
   * 숙소 검색 조건.
   *
   * @param countryCode ISO 3166-1 alpha-2(예: {@code JP}).
   * @param minReviewScore 리뷰 점수 하한(0–10). {@code null}이면 제한 없음.
   */
  public record Stay(
      String cityName,
      String countryCode,
      LocalDate checkin,
      LocalDate checkout,
      int adults,
      Double minReviewScore,
      int limit) {}
}

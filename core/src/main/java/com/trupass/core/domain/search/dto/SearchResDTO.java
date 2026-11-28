package com.trupass.core.domain.search.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SearchResDTO {
  private SearchResDTO() {}

  /**
   * 추천 조합 결과.
   *
   * @param assumptions 빠진 조건을 채웠거나 조건을 풀었을 때 그 내용.
   * @param options AI가 고른 조합, 1–3개. 합계는 core가 계산했다.
   * @param flightCandidates AI에게 보여 준 항공 후보 전체.
   * @param timingsMs 단계별 걸린 시간(ms). plan, flightSearch, staySearch, recommend, total.
   */
  public record Recommendation(
      Criteria criteria,
      List<String> assumptions,
      String summary,
      List<Option> options,
      List<Flight> flightCandidates,
      List<Stay> stayCandidates,
      Ai ai,
      Map<String, Long> timingsMs,
      String dataNotice) {}

  /** 검색에 실제로 쓴 조건. */
  public record Criteria(
      City origin,
      City destination,
      LocalDate departureDate,
      LocalDate returnDate,
      int nights,
      int adults,
      Long budgetKrw,
      Double minReviewScore,
      String departurePeriod) {}

  public record City(String code, String name) {}

  /**
   * @param totalKrw 항공(전체 인원 왕복) + 숙소(전체 기간) 원화 합계.
   * @param withinBudget 예산 안인지. 예산을 말하지 않았으면 {@code null}.
   */
  public record Option(
      int rank,
      String title,
      String reason,
      Flight flight,
      Stay stay,
      long totalKrw,
      Boolean withinBudget) {}

  /**
   * @param price 소스가 준 원래 금액(예: {@code "401.40"}).
   * @param priceKrw 고정 환율로 바꾼 원화. 예산 비교·정렬에 쓴다.
   */
  public record Flight(
      String key,
      String source,
      String carrier,
      String price,
      String currency,
      long priceKrw,
      Instant expiresAt,
      List<Slice> slices) {}

  /** 편도 하나. 시각은 공항 현지 시각. */
  public record Slice(
      String from,
      String to,
      LocalDateTime departingAt,
      LocalDateTime arrivingAt,
      Long durationMinutes,
      int stops,
      List<String> flightNumbers) {}

  /** 숙소 하나. 예약에 쓰는 offer id는 1,000자가 넘어 응답에 넣지 않는다. */
  public record Stay(
      String key,
      String source,
      String hotelId,
      String name,
      Integer stars,
      Double reviewScore,
      Integer reviewCount,
      String address,
      Double latitude,
      Double longitude,
      String room,
      String boardType,
      String boardName,
      boolean refundable,
      String price,
      String currency,
      long priceKrw) {}

  /** AI 호출 정보. 토큰은 두 호출의 합. */
  public record Ai(
      Boolean planGeminiCalled,
      Boolean recommendGeminiCalled,
      Integer promptTokens,
      Integer outputTokens) {}
}

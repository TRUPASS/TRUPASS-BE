package com.trupass.core.domain.search.service;

import com.trupass.core.domain.search.service.CityCatalog.City;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * AI가 뽑은 조건을 검색에 바로 쓸 수 있게 확정한 값. 빠진 값을 기본값으로 채웠다면 그 내용을 {@code assumptions}에 남긴다(사용자에게 그대로 보여 준다).
 *
 * @param minReviewScore 리뷰 점수 하한 0–10. AI의 1–5 평점을 2배 한 값.
 * @param departureTimeFrom 가는 편 출발 시각 범위. 시간대를 말하지 않았으면 {@code null}.
 */
public record SearchCriteria(
    City origin,
    City destination,
    LocalDate departureDate,
    LocalDate returnDate,
    int adults,
    Long budgetKrw,
    Double minReviewScore,
    String departurePeriod,
    LocalTime departureTimeFrom,
    LocalTime departureTimeTo,
    String purpose,
    String coreTravelGoal,
    String desiredSatisfaction,
    List<String> accommodationPreferences,
    List<String> assumptions) {

  public int nights() {
    return (int) ChronoUnit.DAYS.between(departureDate, returnDate);
  }
}

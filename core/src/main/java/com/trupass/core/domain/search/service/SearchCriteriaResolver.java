package com.trupass.core.domain.search.service;

import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.domain.search.service.CityCatalog.City;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AI 조건({@code TripRequirements}) → 검색 조건. 규칙은 search-domain/ai-contract.md와 맞춘다.
 *
 * <ul>
 *   <li>목적지는 반드시 있어야 하고 지원 도시여야 한다.
 *   <li>출발지: AI 값 → {@code originHint} → 서울(SEL).
 *   <li>출발일은 반드시 있어야 하고 오늘(서울 기준) 이후여야 한다. 돌아오는 날이 없으면 2박 3일로 본다.
 *   <li>인원이 없으면 1명. Duffel 한 번 검색에 9명까지라 그보다 많으면 거절한다.
 *   <li>후기 평점(1–5)은 ×2 해서 리뷰 점수(0–10)로 쓴다.
 * </ul>
 */
@Component
public class SearchCriteriaResolver {
  static final int DEFAULT_NIGHTS = 2;
  static final int MAX_TRAVELERS = 9;
  private static final String DEFAULT_ORIGIN = "SEL";

  private final CityCatalog cities;

  public SearchCriteriaResolver(CityCatalog cities) {
    this.cities = cities;
  }

  public SearchCriteria resolve(
      AgentClientResDTO.Requirements requirements, String originHint, LocalDate today) {
    List<String> assumptions = new ArrayList<>();

    if (requirements.destination() == null || requirements.destination().isBlank()) {
      throw new GeneralException(SearchErrorCode.DESTINATION_REQUIRED);
    }
    City destination =
        cities
            .find(requirements.destination())
            .orElseThrow(() -> new GeneralException(SearchErrorCode.UNSUPPORTED_DESTINATION));
    City origin = resolveOrigin(requirements.origin(), originHint, assumptions);
    if (origin.code().equals(destination.code())) {
      throw new GeneralException(SearchErrorCode.SAME_CITY);
    }

    LocalDate departure = requirements.departureDate();
    if (departure == null) {
      throw new GeneralException(SearchErrorCode.DEPARTURE_DATE_REQUIRED);
    }
    if (departure.isBefore(today)) {
      throw new GeneralException(SearchErrorCode.INVALID_TRIP_DATES);
    }
    LocalDate returnDate = requirements.returnDate();
    if (returnDate == null) {
      returnDate = departure.plusDays(DEFAULT_NIGHTS);
      assumptions.add("돌아오는 날을 몰라 " + DEFAULT_NIGHTS + "박 " + (DEFAULT_NIGHTS + 1) + "일로 검색했습니다.");
    } else if (!returnDate.isAfter(departure)) {
      throw new GeneralException(SearchErrorCode.INVALID_TRIP_DATES);
    }

    int adults;
    if (requirements.travelers() == null) {
      adults = 1;
      assumptions.add("인원을 몰라 1명으로 검색했습니다.");
    } else if (requirements.travelers() > MAX_TRAVELERS) {
      throw new GeneralException(SearchErrorCode.TOO_MANY_TRAVELERS);
    } else {
      adults = requirements.travelers();
    }

    Double minReviewScore = null;
    if (requirements.minTravelerRating() != null) {
      minReviewScore =
          BigDecimal.valueOf(requirements.minTravelerRating())
              .multiply(BigDecimal.TWO)
              .setScale(1, RoundingMode.HALF_UP)
              .doubleValue();
      assumptions.add(
          "후기 "
              + requirements.minTravelerRating()
              + "점(5점 만점)을 숙소 리뷰 점수 "
              + minReviewScore
              + "점(10점 만점) 이상으로 바꿔 적용했습니다.");
    }

    LocalTime from = null;
    LocalTime to = null;
    String period = requirements.departurePeriod();
    if (period != null) {
      switch (period) {
        case "morning" -> {
          from = LocalTime.of(6, 0);
          to = LocalTime.of(12, 0);
        }
        case "afternoon" -> {
          from = LocalTime.of(12, 0);
          to = LocalTime.of(18, 0);
        }
        case "evening" -> {
          from = LocalTime.of(18, 0);
          to = LocalTime.of(23, 59);
        }
        default -> assumptions.add("출발 시간대 '" + period + "'를 알 수 없어 시간 제한 없이 검색했습니다.");
      }
    }

    List<String> transport = nullToEmpty(requirements.transportPreferences());
    if (!transport.isEmpty() && !transport.contains("flight")) {
      assumptions.add("지금은 항공편만 검색합니다(요청한 교통수단: " + String.join(", ", transport) + ").");
    }
    if (!nullToEmpty(requirements.additionalCities()).isEmpty()) {
      assumptions.add(
          "추가 도시("
              + String.join(", ", requirements.additionalCities())
              + ")는 아직 검색하지 않고 "
              + destination.nameKo()
              + "만 검색했습니다.");
    }

    return new SearchCriteria(
        origin,
        destination,
        departure,
        returnDate,
        adults,
        requirements.budgetKrw(),
        minReviewScore,
        from == null ? null : period,
        from,
        to,
        requirements.purpose(),
        requirements.coreTravelGoal(),
        requirements.desiredSatisfaction(),
        nullToEmpty(requirements.accommodationPreferences()),
        List.copyOf(assumptions));
  }

  private City resolveOrigin(String origin, String originHint, List<String> assumptions) {
    for (String text : new String[] {origin, originHint}) {
      if (text == null || text.isBlank()) {
        continue;
      }
      var city = cities.find(text);
      if (city.isPresent()) {
        return city.get();
      }
      assumptions.add("출발지 '" + text + "'를 찾지 못했습니다.");
    }
    City fallback = cities.byCode(DEFAULT_ORIGIN).orElseThrow();
    if (origin == null || origin.isBlank()) {
      assumptions.add("출발지를 " + fallback.nameKo() + "(" + fallback.code() + ")로 검색했습니다.");
    } else {
      assumptions.add("출발지를 " + fallback.nameKo() + "(" + fallback.code() + ")로 바꿔 검색했습니다.");
    }
    return fallback;
  }

  private static List<String> nullToEmpty(List<String> values) {
    return values == null ? List.of() : values;
  }
}

package com.trupass.core.domain.search.service;

import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightOffer;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightSegment;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightSlice;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.StayOffer;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import com.trupass.core.global.common.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** search service 테스트용 데이터. 2026-12-20 서울 → 오사카 2박, 2명 기준. */
final class SearchFixtures {
  static final LocalDate TODAY = LocalDate.of(2026, 11, 28);
  static final LocalDate DEPARTURE = LocalDate.of(2026, 12, 20);
  static final LocalDate RETURN = LocalDate.of(2026, 12, 22);

  private SearchFixtures() {}

  static KrwConverter usd1400() {
    return new KrwConverter(Map.of("USD", new BigDecimal("1400")));
  }

  /** 직항 왕복. 가는 편은 {@code departs}에 GMP → KIX, 오는 편은 12-22 10:00 KIX → GMP. */
  static FlightOffer flight(String ownerCode, String usd, String flightNumber, LocalTime departs) {
    LocalDateTime out = DEPARTURE.atTime(departs);
    LocalDateTime back = RETURN.atTime(10, 0);
    return new FlightOffer(
        "DUFFEL_TEST",
        "off_" + flightNumber + "_" + usd,
        ownerCode,
        ownerCode + " Airways",
        Money.fromDecimal(new BigDecimal(usd), "USD"),
        null,
        List.of(
            slice("GMP", "KIX", flightNumber, out, Duration.ofMinutes(110)),
            slice("KIX", "GMP", flightNumber + "R", back, Duration.ofMinutes(115))));
  }

  static FlightSlice slice(
      String from, String to, String flightNumber, LocalDateTime departs, Duration duration) {
    LocalDateTime arrives = departs.plus(duration == null ? Duration.ofHours(2) : duration);
    return new FlightSlice(
        from,
        to,
        departs,
        arrives,
        duration,
        List.of(new FlightSegment(flightNumber, "Carrier", from, to, departs, arrives)));
  }

  static StayOffer stay(String hotelId, long krw, Double score, Integer reviews) {
    return stay(hotelId, krw, score, reviews, true);
  }

  static StayOffer stay(
      String hotelId, long krw, Double score, Integer reviews, boolean taxesIncluded) {
    return new StayOffer(
        "LITEAPI_SANDBOX",
        hotelId,
        "offer_" + hotelId,
        "Hotel " + hotelId,
        4,
        score,
        reviews,
        "Osaka " + hotelId,
        34.66,
        135.50,
        "Double Room",
        "RO",
        "Room Only",
        true,
        taxesIncluded,
        Money.krw(krw));
  }

  static SearchCriteria criteria(
      Long budgetKrw, Double minReviewScore, LocalTime from, LocalTime to) {
    CityCatalog cities = new CityCatalog();
    return new SearchCriteria(
        cities.byCode("SEL").orElseThrow(),
        cities.byCode("OSA").orElseThrow(),
        DEPARTURE,
        RETURN,
        2,
        budgetKrw,
        minReviewScore,
        from == null ? null : "morning",
        from,
        to,
        "휴식",
        "맛집",
        "편하게",
        List.of("역 근처"),
        List.of());
  }

  static SearchCriteria criteria() {
    return criteria(null, null, null, null);
  }

  /** AI가 뽑았다고 치는 조건: 서울 → 오사카, 12-20 ~ 12-22, 2명, 예산 120만 원. */
  static AgentClientResDTO.Requirements requirements(Long budgetKrw) {
    return new AgentClientResDTO.Requirements(
        "서울",
        "오사카",
        DEPARTURE,
        RETURN,
        2,
        budgetKrw,
        "휴식",
        "맛집",
        "편하게",
        null,
        List.of("역 근처"),
        List.of("flight"),
        null,
        List.of());
  }
}

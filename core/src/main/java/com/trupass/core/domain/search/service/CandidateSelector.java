package com.trupass.core.domain.search.service;

import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightOffer;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightSlice;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.StayOffer;
import com.trupass.core.global.common.Money;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 검색 결과 중 AI에게 넘길 후보를 고른다. 항공 수십 개, 숙소 20개를 그대로 넘기면 토큰이 많이 들고 AI가 헷갈리므로 core가 먼저 줄인다.
 *
 * <p>후보마다 짧은 key(F1, S1 …)를 붙인다. AI는 key로만 조합을 고르고, 금액 합계는 core가 key로 다시 찾아 계산한다.
 */
@Component
public class CandidateSelector {
  static final int MAX_FLIGHTS = 8;
  static final int CHEAPEST_STAYS = 6;
  static final int TOP_RATED_STAYS = 4;
  static final int MIN_REVIEW_COUNT = 20;

  /** Duffel 테스트 모드의 가짜 항공사. 실제처럼 보이지 않아 뺀다. */
  static final String FAKE_AIRLINE = "ZZ";

  private final KrwConverter krw;

  public CandidateSelector(KrwConverter krw) {
    this.krw = krw;
  }

  /** 원화 환산값과 key를 붙인 항공 후보. */
  public record FlightCandidate(String key, FlightOffer offer, Money priceKrw) {}

  /** 원화 환산값과 key를 붙인 숙소 후보. */
  public record StayCandidate(String key, StayOffer offer, Money priceKrw) {}

  /**
   * @param notes 고르는 중에 조건을 풀었으면 그 이유. 사용자에게 보여 준다.
   */
  public record Candidates(
      List<FlightCandidate> flights, List<StayCandidate> stays, List<String> notes) {}

  public Candidates select(
      List<FlightOffer> flightOffers, List<StayOffer> stayOffers, SearchCriteria criteria) {
    List<String> notes = new ArrayList<>();
    return new Candidates(
        selectFlights(flightOffers, criteria, notes),
        selectStays(stayOffers, criteria, notes),
        List.copyOf(notes));
  }

  List<FlightCandidate> selectFlights(
      List<FlightOffer> offers, SearchCriteria criteria, List<String> notes) {
    // 같은 비행기 조합(편명 + 출발 시각)이 여러 운임으로 오면 가장 싼 것만 남긴다.
    Map<String, Priced<FlightOffer>> cheapestByItinerary = new LinkedHashMap<>();
    for (FlightOffer offer : offers) {
      if (FAKE_AIRLINE.equals(offer.ownerCode())) {
        continue;
      }
      Priced<FlightOffer> priced = new Priced<>(offer, krw.toKrw(offer.price()));
      cheapestByItinerary.merge(
          itineraryKey(offer), priced, (a, b) -> a.krw().amount() <= b.krw().amount() ? a : b);
    }
    List<Priced<FlightOffer>> pool = new ArrayList<>(cheapestByItinerary.values());

    if (criteria.departureTimeFrom() != null) {
      List<Priced<FlightOffer>> inWindow =
          pool.stream()
              .filter(p ->
                  departsWithin(
                      p.value(), criteria.departureTimeFrom(), criteria.departureTimeTo()))
              .toList();
      if (inWindow.isEmpty()) {
        notes.add("원하는 출발 시간대에 맞는 항공편이 없어 시간대 조건 없이 골랐습니다.");
      } else {
        pool = new ArrayList<>(inWindow);
      }
    }

    pool.sort(
        Comparator.comparingLong((Priced<FlightOffer> p) -> p.krw().amount())
            .thenComparing(p -> totalDuration(p.value())));

    List<FlightCandidate> result = new ArrayList<>();
    for (int i = 0; i < Math.min(MAX_FLIGHTS, pool.size()); i++) {
      Priced<FlightOffer> p = pool.get(i);
      result.add(new FlightCandidate("F" + (i + 1), p.value(), p.krw()));
    }
    return List.copyOf(result);
  }

  List<StayCandidate> selectStays(
      List<StayOffer> offers, SearchCriteria criteria, List<String> notes) {
    // 세금 별도 요금은 총액 비교가 틀어지므로 뺀다(LiteAPI 1,279건 중 10건 정도).
    List<Priced<StayOffer>> pool =
        offers.stream()
            .filter(StayOffer::taxesIncluded)
            .map(o -> new Priced<>(o, krw.toKrw(o.price())))
            .toList();

    pool =
        softFilter(
            pool,
            p -> p.value().reviewCount() != null && p.value().reviewCount() >= MIN_REVIEW_COUNT,
            "리뷰가 " + MIN_REVIEW_COUNT + "개 이상인 숙소가 없어 리뷰 수 조건 없이 골랐습니다.",
            notes);
    if (criteria.minReviewScore() != null) {
      double min = criteria.minReviewScore();
      pool =
          softFilter(
              pool,
              p -> p.value().reviewScore() != null && p.value().reviewScore() >= min,
              "리뷰 점수 " + min + "점 이상인 숙소가 없어 점수 조건 없이 골랐습니다.",
              notes);
    }

    Comparator<Priced<StayOffer>> byPrice = Comparator.comparingLong(p -> p.krw().amount());
    Comparator<Priced<StayOffer>> byRating =
        Comparator.comparing(
                (Priced<StayOffer> p) -> p.value().reviewScore(),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(byPrice);

    // 싼 곳만 넘기면 AI가 "만족도" 쪽 조합을 짤 수 없으므로 평점 높은 곳을 몇 개 섞는다.
    Set<Priced<StayOffer>> picked = new LinkedHashSet<>();
    pool.stream().sorted(byPrice).limit(CHEAPEST_STAYS).forEach(picked::add);
    pool.stream()
        .sorted(byRating)
        .filter(p -> !picked.contains(p))
        .limit(TOP_RATED_STAYS)
        .forEach(picked::add);

    List<Priced<StayOffer>> sorted = picked.stream().sorted(byPrice).toList();
    List<StayCandidate> result = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++) {
      result.add(new StayCandidate("S" + (i + 1), sorted.get(i).value(), sorted.get(i).krw()));
    }
    return List.copyOf(result);
  }

  private static <T> List<T> softFilter(
      List<T> pool, Predicate<T> condition, String noteIfEmpty, List<String> notes) {
    List<T> filtered = pool.stream().filter(condition).toList();
    if (filtered.isEmpty() && !pool.isEmpty()) {
      notes.add(noteIfEmpty);
      return pool;
    }
    return filtered;
  }

  static String itineraryKey(FlightOffer offer) {
    return offer.slices().stream()
        .flatMap(slice -> slice.segments().stream())
        .map(s -> s.flightNumber() + "@" + s.departingAt())
        .collect(Collectors.joining("|"));
  }

  /** 가는 편 출발 시각이 [from, to) 안에 있는지. 끝이 23:59면 그 시각도 포함한다. */
  static boolean departsWithin(FlightOffer offer, LocalTime from, LocalTime to) {
    LocalTime departure = offer.slices().getFirst().departingAt().toLocalTime();
    boolean beforeEnd =
        to.equals(LocalTime.of(23, 59)) ? !departure.isAfter(to) : departure.isBefore(to);
    return !departure.isBefore(from) && beforeEnd;
  }

  /** 비행 시간 합계. Duffel이 {@code duration}을 안 주는 편도는 0으로 본다(정렬 보조용). */
  static Duration totalDuration(FlightOffer offer) {
    return offer.slices().stream()
        .map(FlightSlice::duration)
        .filter(Objects::nonNull)
        .reduce(Duration.ZERO, Duration::plus);
  }

  /** 검색 결과에 원화 환산값을 붙여 둔 것. equals는 identity로 쓴다. */
  private static final class Priced<T> {
    private final T value;
    private final Money krw;

    Priced(T value, Money krw) {
      this.value = value;
      this.krw = krw;
    }

    T value() {
      return value;
    }

    Money krw() {
      return krw;
    }
  }
}

package com.trupass.core.domain.search.service;

import com.trupass.core.domain.search.client.FlightSearchClient;
import com.trupass.core.domain.search.client.StaySearchClient;
import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightOffer;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightSegment;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightSlice;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.StayOffer;
import com.trupass.core.domain.search.dto.SearchReqDTO;
import com.trupass.core.domain.search.dto.SearchResDTO;
import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.domain.search.service.CandidateSelector.Candidates;
import com.trupass.core.domain.search.service.CandidateSelector.FlightCandidate;
import com.trupass.core.domain.search.service.CandidateSelector.StayCandidate;
import com.trupass.core.domain.search.service.CityCatalog.City;
import com.trupass.core.domain.trip.client.AgentClient;
import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import com.trupass.core.domain.trip.exception.TripErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.common.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 문장 3개 → AI 조건 추출 → 항공·숙소 검색(동시에) → 후보 고르기 → AI 조합 추천 → 합계 계산.
 *
 * <p>스파이크용 동기 흐름이다. DB에 저장하지 않고, 트랜잭션도 없다. M3에서 {@code POST /searches} 비동기 흐름으로 옮긴다.
 *
 * <p>역할 나누기: AI는 조건을 뽑고 후보 key로 조합을 고른다. 금액 환산·합계·예산 비교는 core가 한다. AI가 없는 key를 주면 그 조합은 버린다.
 */
@Service
public class TripRecommendationService {
  private static final Logger log = LoggerFactory.getLogger(TripRecommendationService.class);

  static final int MAX_OPTIONS = 3;
  static final int MAX_CONNECTIONS = 1;
  static final int STAY_LIMIT = 20;
  static final String DATA_NOTICE = "항공은 Duffel 테스트 모드, 숙소는 LiteAPI 샌드박스 데이터라 가격이 실제 판매가가 아닙니다.";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter LOCAL_MINUTES =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private final AgentClient agentClient;
  private final FlightSearchClient flightSearchClient;
  private final StaySearchClient staySearchClient;
  private final SearchCriteriaResolver resolver;
  private final CandidateSelector selector;
  private final Clock clock;

  public TripRecommendationService(
      AgentClient agentClient,
      FlightSearchClient flightSearchClient,
      StaySearchClient staySearchClient,
      SearchCriteriaResolver resolver,
      CandidateSelector selector,
      Clock clock) {
    this.agentClient = agentClient;
    this.flightSearchClient = flightSearchClient;
    this.staySearchClient = staySearchClient;
    this.resolver = resolver;
    this.selector = selector;
    this.clock = clock;
  }

  public SearchResDTO.Recommendation recommend(SearchReqDTO.Recommend request) {
    long started = System.nanoTime();
    Map<String, Long> timings = new LinkedHashMap<>();

    AgentClientResDTO.Plan plan =
        timed(
            "plan",
            timings,
            () ->
                agentClient.plan(
                    new AgentClientReqDTO.Plan(
                        request.prompt(),
                        request.accommodationPrompt(),
                        request.purposePrompt(),
                        request.originHint())));

    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    SearchCriteria criteria = resolver.resolve(plan.requirements(), request.originHint(), today);

    SearchResults results = searchInParallel(criteria, timings);
    if (results.flights().isEmpty()) {
      throw new GeneralException(SearchErrorCode.NO_FLIGHT_FOUND);
    }
    if (results.stays().isEmpty()) {
      throw new GeneralException(SearchErrorCode.NO_STAY_FOUND);
    }

    Candidates candidates = selector.select(results.flights(), results.stays(), criteria);
    if (candidates.flights().isEmpty()) {
      throw new GeneralException(SearchErrorCode.NO_FLIGHT_FOUND);
    }
    if (candidates.stays().isEmpty()) {
      throw new GeneralException(SearchErrorCode.NO_STAY_FOUND);
    }

    AgentClientResDTO.Recommend recommendation =
        timed(
            "recommend",
            timings,
            () -> agentClient.recommend(toRecommendRequest(criteria, candidates)));
    List<SearchResDTO.Option> options = toOptions(recommendation, candidates, criteria);
    if (options.isEmpty()) {
      throw new GeneralException(TripErrorCode.INVALID_PACKAGE);
    }
    timings.put("total", millisSince(started));

    log.info(
        "trip recommendation {}→{} {}~{} adults={} flights={}/{} stays={}/{} options={} timings={}",
        criteria.origin().code(),
        criteria.destination().code(),
        criteria.departureDate(),
        criteria.returnDate(),
        criteria.adults(),
        candidates.flights().size(),
        results.flights().size(),
        candidates.stays().size(),
        results.stays().size(),
        options.size(),
        timings);

    List<String> assumptions = new ArrayList<>(criteria.assumptions());
    assumptions.addAll(candidates.notes());
    return new SearchResDTO.Recommendation(
        toCriteriaView(criteria),
        List.copyOf(assumptions),
        recommendation.summary(),
        options,
        candidates.flights().stream().map(TripRecommendationService::toFlightView).toList(),
        candidates.stays().stream().map(TripRecommendationService::toStayView).toList(),
        new SearchResDTO.Ai(
            plan.geminiCalled(),
            recommendation.geminiCalled(),
            sum(plan.promptTokens(), recommendation.promptTokens()),
            sum(plan.outputTokens(), recommendation.outputTokens())),
        timings,
        DATA_NOTICE);
  }

  // ---- 검색 ----

  record SearchResults(List<FlightOffer> flights, List<StayOffer> stays) {}

  private record Timed<T>(T value, long millis) {}

  /**
   * 항공과 숙소는 서로 기다릴 필요가 없어 가상 스레드 두 개로 동시에 부른다. 한쪽이 실패하면 다른 쪽은 취소하고 그 예외를 그대로 던진다.
   *
   * <p>출발 시간대는 Duffel에 넘기지 않는다. 넘기면 그 시간대에 편이 없을 때 다시 불러야 해서, 넓게 받아 온 뒤 {@link CandidateSelector}가
   * 거른다.
   */
  SearchResults searchInParallel(SearchCriteria criteria, Map<String, Long> timings) {
    var flightRequest =
        new SearchClientReqDTO.Flight(
            criteria.origin().code(),
            criteria.destination().code(),
            criteria.departureDate(),
            criteria.returnDate(),
            criteria.adults(),
            null,
            null,
            MAX_CONNECTIONS);
    var stayRequest =
        new SearchClientReqDTO.Stay(
            criteria.destination().nameEn(),
            criteria.destination().countryCode(),
            criteria.departureDate(),
            criteria.returnDate(),
            criteria.adults(),
            criteria.minReviewScore(),
            STAY_LIMIT);

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Timed<List<FlightOffer>>> flights =
          executor.submit(() -> measure(() -> flightSearchClient.search(flightRequest)));
      Future<Timed<List<StayOffer>>> stays =
          executor.submit(() -> measure(() -> staySearchClient.search(stayRequest)));

      Timed<List<FlightOffer>> flightResult = await(flights, stays);
      Timed<List<StayOffer>> stayResult = await(stays, flights);
      timings.put("flightSearch", flightResult.millis());
      timings.put("staySearch", stayResult.millis());
      return new SearchResults(flightResult.value(), stayResult.value());
    }
  }

  private static <T> T await(Future<T> future, Future<?> other) {
    try {
      return future.get();
    } catch (ExecutionException e) {
      other.cancel(true);
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      other.cancel(true);
      throw new IllegalStateException("search interrupted", e);
    }
  }

  // ---- AI에게 넘길 모양 ----

  AgentClientReqDTO.Recommend toRecommendRequest(SearchCriteria criteria, Candidates candidates) {
    var trip =
        new AgentClientReqDTO.Trip(
            label(criteria.origin()),
            label(criteria.destination()),
            criteria.departureDate().toString(),
            criteria.returnDate().toString(),
            criteria.nights(),
            criteria.adults(),
            criteria.budgetKrw(),
            criteria.purpose(),
            criteria.coreTravelGoal(),
            criteria.desiredSatisfaction(),
            criteria.accommodationPreferences(),
            criteria.departurePeriod());
    var flights =
        candidates.flights().stream()
            .map(c ->
                new AgentClientReqDTO.FlightCandidate(
                    c.key(),
                    c.offer().ownerName(),
                    c.priceKrw().amount(),
                    toLeg(c.offer().slices().getFirst()),
                    c.offer().slices().size() > 1 ? toLeg(c.offer().slices().get(1)) : null))
            .toList();
    var stays =
        candidates.stays().stream()
            .map(c ->
                new AgentClientReqDTO.StayCandidate(
                    c.key(),
                    c.offer().name(),
                    c.offer().stars(),
                    c.offer().reviewScore(),
                    c.offer().reviewCount(),
                    c.priceKrw().amount(),
                    c.offer().roomName(),
                    c.offer().boardName(),
                    c.offer().refundable(),
                    c.offer().address()))
            .toList();
    return new AgentClientReqDTO.Recommend(trip, flights, stays, MAX_OPTIONS);
  }

  private static AgentClientReqDTO.FlightLeg toLeg(FlightSlice slice) {
    return new AgentClientReqDTO.FlightLeg(
        slice.originAirport(),
        slice.destinationAirport(),
        format(slice.departingAt()),
        format(slice.arrivingAt()),
        slice.duration() == null ? null : slice.duration().toMinutes(),
        slice.stops(),
        flightNumbers(slice));
  }

  // ---- AI 응답 → 조합 ----

  /**
   * AI가 고른 key를 후보에서 다시 찾아 합계를 계산한다. 없는 key, 같은 조합 반복은 버리고 최대 {@value #MAX_OPTIONS}개만 남긴다.
   *
   * <p>합계 = 항공(전체 인원 왕복) + 숙소(전체 기간). 둘 다 원화 환산값이다. 돈 계산이라 리뷰어 2명 규칙 대상이다.
   */
  List<SearchResDTO.Option> toOptions(
      AgentClientResDTO.Recommend recommendation, Candidates candidates, SearchCriteria criteria) {
    Map<String, FlightCandidate> flights = byKey(candidates.flights(), FlightCandidate::key);
    Map<String, StayCandidate> stays = byKey(candidates.stays(), StayCandidate::key);
    Set<String> seen = new HashSet<>();
    List<SearchResDTO.Option> options = new ArrayList<>();

    for (AgentClientResDTO.Option option : recommendation.options()) {
      if (option == null) {
        continue;
      }
      FlightCandidate flight = flights.get(option.flightKey());
      StayCandidate stay = stays.get(option.stayKey());
      if (flight == null || stay == null) {
        log.warn(
            "agent picked unknown candidate flight={} stay={}; dropped",
            option.flightKey(),
            option.stayKey());
        continue;
      }
      if (!seen.add(flight.key() + "/" + stay.key())) {
        continue;
      }
      Money total = flight.priceKrw().plus(stay.priceKrw());
      Boolean withinBudget =
          criteria.budgetKrw() == null ? null : total.amount() <= criteria.budgetKrw();
      options.add(
          new SearchResDTO.Option(
              options.size() + 1,
              option.title(),
              option.reason(),
              toFlightView(flight),
              toStayView(stay),
              total.amount(),
              withinBudget));
      if (options.size() == MAX_OPTIONS) {
        break;
      }
    }
    return List.copyOf(options);
  }

  // ---- 응답 모양 ----

  private static SearchResDTO.Criteria toCriteriaView(SearchCriteria criteria) {
    return new SearchResDTO.Criteria(
        new SearchResDTO.City(criteria.origin().code(), criteria.origin().nameKo()),
        new SearchResDTO.City(criteria.destination().code(), criteria.destination().nameKo()),
        criteria.departureDate(),
        criteria.returnDate(),
        criteria.nights(),
        criteria.adults(),
        criteria.budgetKrw(),
        criteria.minReviewScore(),
        criteria.departurePeriod());
  }

  private static SearchResDTO.Flight toFlightView(FlightCandidate candidate) {
    FlightOffer offer = candidate.offer();
    return new SearchResDTO.Flight(
        candidate.key(),
        offer.source(),
        offer.ownerName(),
        offer.price().toDecimal().toPlainString(),
        offer.price().currency(),
        candidate.priceKrw().amount(),
        offer.expiresAt(),
        offer.slices().stream()
            .map(slice ->
                new SearchResDTO.Slice(
                    slice.originAirport(),
                    slice.destinationAirport(),
                    slice.departingAt(),
                    slice.arrivingAt(),
                    slice.duration() == null ? null : slice.duration().toMinutes(),
                    slice.stops(),
                    flightNumbers(slice)))
            .toList());
  }

  private static SearchResDTO.Stay toStayView(StayCandidate candidate) {
    StayOffer offer = candidate.offer();
    return new SearchResDTO.Stay(
        candidate.key(),
        offer.source(),
        offer.hotelId(),
        offer.name(),
        offer.stars(),
        offer.reviewScore(),
        offer.reviewCount(),
        offer.address(),
        offer.latitude(),
        offer.longitude(),
        offer.roomName(),
        offer.boardType(),
        offer.boardName(),
        offer.refundable(),
        offer.price().toDecimal().toPlainString(),
        offer.price().currency(),
        candidate.priceKrw().amount());
  }

  // ---- 작은 도우미 ----

  private static String label(City city) {
    return city.nameKo() + "(" + city.code() + ")";
  }

  private static String format(LocalDateTime time) {
    return time == null ? null : time.format(LOCAL_MINUTES);
  }

  private static List<String> flightNumbers(FlightSlice slice) {
    return slice.segments().stream().map(FlightSegment::flightNumber).toList();
  }

  private static <T> Map<String, T> byKey(List<T> items, Function<T, String> key) {
    return items.stream().collect(Collectors.toMap(key, item -> item));
  }

  private static Integer sum(Integer a, Integer b) {
    if (a == null && b == null) {
      return null;
    }
    return (a == null ? 0 : a) + (b == null ? 0 : b);
  }

  private static <T> T timed(String name, Map<String, Long> timings, Supplier<T> call) {
    long started = System.nanoTime();
    try {
      return call.get();
    } finally {
      timings.put(name, millisSince(started));
    }
  }

  private static <T> Timed<T> measure(Supplier<T> call) {
    long started = System.nanoTime();
    T value = call.get();
    return new Timed<>(value, millisSince(started));
  }

  private static long millisSince(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }
}

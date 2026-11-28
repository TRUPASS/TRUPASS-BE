package com.trupass.core.domain.search.service;

import static com.trupass.core.domain.search.service.SearchFixtures.DEPARTURE;
import static com.trupass.core.domain.search.service.SearchFixtures.RETURN;
import static com.trupass.core.domain.search.service.SearchFixtures.flight;
import static com.trupass.core.domain.search.service.SearchFixtures.stay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightOffer;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.StayOffer;
import com.trupass.core.domain.search.dto.SearchReqDTO;
import com.trupass.core.domain.search.dto.SearchResDTO;
import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.domain.trip.client.AgentClient;
import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO.Requirements;
import com.trupass.core.domain.trip.exception.TripErrorCode;
import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class TripRecommendationServiceTest {
  /** 서울 시각 2026-11-28 12:00. */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-28T03:00:00Z"), ZoneOffset.UTC);

  private static final SearchReqDTO.Recommend REQUEST =
      new SearchReqDTO.Recommend("12월 20일부터 2박 3일 오사카, 2명, 120만 원", "역 근처", "맛집", null);

  private final FakeAgent agent = new FakeAgent();
  private final List<SearchClientReqDTO.Flight> flightRequests = new CopyOnWriteArrayList<>();
  private final List<SearchClientReqDTO.Stay> stayRequests = new CopyOnWriteArrayList<>();

  // NH 401.40달러 → 561,960원(F1), OZ 447.70달러 → 626,780원(F2). ZZ는 가짜 항공사라 빠진다.
  private List<FlightOffer> flightResults =
      List.of(
          flight("OZ", "447.70", "OZ1145", LocalTime.of(7, 55)),
          flight("ZZ", "85.05", "ZZ100", LocalTime.of(14, 42)),
          flight("NH", "401.40", "NH862", LocalTime.of(7, 45)));

  // 가격순 key: keihan S1(153,477) · citadines S2(388,805) · swiss S3(700,000)
  private List<StayOffer> stayResults =
      List.of(
          stay("citadines", 388_805, 9.2, 5037),
          stay("keihan", 153_477, 8.4, 2100),
          stay("swiss", 700_000, 9.0, 3000));

  private RuntimeException stayFailure;

  private final TripRecommendationService service =
      new TripRecommendationService(
          agent,
          request -> {
            flightRequests.add(request);
            return flightResults;
          },
          request -> {
            stayRequests.add(request);
            if (stayFailure != null) {
              throw stayFailure;
            }
            return stayResults;
          },
          new SearchCriteriaResolver(new CityCatalog()),
          new CandidateSelector(SearchFixtures.usd1400()),
          CLOCK);

  /** plan은 정해 둔 조건을 주고, recommend는 받은 요청을 기억해 두고 정해 둔 조합을 준다. */
  private static final class FakeAgent implements AgentClient {
    Requirements requirements = SearchFixtures.requirements(1_200_000L);
    List<AgentClientResDTO.Option> options = List.of(option("F1", "S2"));
    AgentClientReqDTO.Recommend lastRecommend;
    int recommendCalls;

    @Override
    public AgentClientResDTO.Plan plan(AgentClientReqDTO.Plan request) {
      return new AgentClientResDTO.Plan("prompt_1", requirements, true, 100, 20);
    }

    @Override
    public AgentClientResDTO.Recommend recommend(AgentClientReqDTO.Recommend request) {
      lastRecommend = request;
      recommendCalls++;
      return new AgentClientResDTO.Recommend("오사카 2박 조합입니다.", options, true, 300, 50);
    }
  }

  private static AgentClientResDTO.Option option(String flightKey, String stayKey) {
    return new AgentClientResDTO.Option(
        flightKey, stayKey, flightKey + "+" + stayKey, "이유 " + flightKey + "/" + stayKey);
  }

  /** {@link SearchFixtures#requirements}에 평점(5점 만점)과 출발 시간대만 바꾼 것. */
  private static Requirements requirements(Double rating, String period) {
    Requirements base = SearchFixtures.requirements(1_200_000L);
    return new Requirements(
        base.origin(),
        base.destination(),
        base.departureDate(),
        base.returnDate(),
        base.travelers(),
        base.budgetKrw(),
        base.purpose(),
        base.coreTravelGoal(),
        base.desiredSatisfaction(),
        rating,
        base.accommodationPreferences(),
        base.transportPreferences(),
        period,
        base.additionalCities());
  }

  private BaseErrorCode errorOfRecommend() {
    try {
      service.recommend(REQUEST);
    } catch (GeneralException e) {
      return e.getErrorCode();
    }
    throw new AssertionError("expected GeneralException");
  }

  @Test
  void AI가_고른_key로_조합을_만들고_합계는_core가_계산한다() {
    agent.options =
        List.of(
            option("F1", "S2"),
            option("F9", "S1"), // 없는 항공 key → 버린다
            option("F1", "S2"), // 같은 조합 반복 → 버린다
            option("F2", "S3"));

    SearchResDTO.Recommendation result = service.recommend(REQUEST);

    assertThat(result.options()).hasSize(2);
    SearchResDTO.Option first = result.options().get(0);
    assertThat(first.rank()).isEqualTo(1);
    assertThat(first.title()).isEqualTo("F1+S2");
    assertThat(first.flight().key()).isEqualTo("F1");
    assertThat(first.flight().carrier()).isEqualTo("NH Airways");
    assertThat(first.flight().price()).isEqualTo("401.40");
    assertThat(first.flight().currency()).isEqualTo("USD");
    assertThat(first.flight().priceKrw()).isEqualTo(561_960);
    assertThat(first.stay().hotelId()).isEqualTo("citadines");
    assertThat(first.stay().price()).isEqualTo("388805");
    assertThat(first.stay().priceKrw()).isEqualTo(388_805);
    // 561,960 + 388,805 = 950,765원 ≤ 예산 1,200,000원
    assertThat(first.totalKrw()).isEqualTo(950_765);
    assertThat(first.withinBudget()).isTrue();

    SearchResDTO.Option second = result.options().get(1);
    assertThat(second.rank()).isEqualTo(2);
    // 626,780 + 700,000 = 1,326,780원 > 예산
    assertThat(second.totalKrw()).isEqualTo(1_326_780);
    assertThat(second.withinBudget()).isFalse();

    assertThat(result.summary()).isEqualTo("오사카 2박 조합입니다.");
    assertThat(result.flightCandidates())
        .extracting(SearchResDTO.Flight::key)
        .containsExactly("F1", "F2");
    assertThat(result.stayCandidates())
        .extracting(SearchResDTO.Stay::hotelId)
        .containsExactly("keihan", "citadines", "swiss");
    assertThat(result.criteria().origin().code()).isEqualTo("SEL");
    assertThat(result.criteria().destination().name()).isEqualTo("오사카");
    assertThat(result.criteria().nights()).isEqualTo(2);
    assertThat(result.assumptions()).isEmpty();
    assertThat(result.ai()).isEqualTo(new SearchResDTO.Ai(true, true, 400, 70));
    assertThat(result.timingsMs())
        .containsOnlyKeys("plan", "flightSearch", "staySearch", "recommend", "total");
    assertThat(result.dataNotice()).isEqualTo(TripRecommendationService.DATA_NOTICE);
  }

  @Test
  void AI에게는_원화_가격과_key만_넘긴다() {
    service.recommend(REQUEST);

    AgentClientReqDTO.Recommend sent = agent.lastRecommend;
    assertThat(sent.maxOptions()).isEqualTo(TripRecommendationService.MAX_OPTIONS);
    assertThat(sent.trip().origin()).isEqualTo("서울(SEL)");
    assertThat(sent.trip().destination()).isEqualTo("오사카(OSA)");
    assertThat(sent.trip().departureDate()).isEqualTo("2026-12-20");
    assertThat(sent.trip().nights()).isEqualTo(2);
    assertThat(sent.trip().travelers()).isEqualTo(2);
    assertThat(sent.trip().budgetKrw()).isEqualTo(1_200_000L);

    AgentClientReqDTO.FlightCandidate f1 = sent.flights().getFirst();
    assertThat(f1.key()).isEqualTo("F1");
    assertThat(f1.priceKrw()).isEqualTo(561_960);
    assertThat(f1.outbound().from()).isEqualTo("GMP");
    assertThat(f1.outbound().departingAt()).isEqualTo("2026-12-20T07:45");
    assertThat(f1.outbound().durationMinutes()).isEqualTo(110L);
    assertThat(f1.outbound().flightNumbers()).containsExactly("NH862");
    assertThat(f1.inbound().from()).isEqualTo("KIX");
    assertThat(sent.stays())
        .extracting(AgentClientReqDTO.StayCandidate::key)
        .containsExactly("S1", "S2", "S3");
  }

  @Test
  void 조합은_최대_3개다() {
    agent.options =
        List.of(option("F1", "S1"), option("F1", "S2"), option("F2", "S1"), option("F2", "S2"));

    assertThat(service.recommend(REQUEST).options())
        .extracting(SearchResDTO.Option::rank)
        .containsExactly(1, 2, 3);
  }

  @Test
  void 예산을_말하지_않았으면_예산_안인지_판단하지_않는다() {
    agent.requirements = SearchFixtures.requirements(null);

    SearchResDTO.Option option = service.recommend(REQUEST).options().getFirst();

    assertThat(option.totalKrw()).isEqualTo(950_765);
    assertThat(option.withinBudget()).isNull();
  }

  @Test
  void AI가_고른_key가_모두_없으면_실패한다() {
    agent.options = List.of(option("F9", "S1"), option("F1", "S99"));

    assertThat(errorOfRecommend()).isEqualTo(TripErrorCode.INVALID_PACKAGE);
  }

  @Test
  void 항공편이_없으면_AI를_부르지_않고_실패한다() {
    flightResults = List.of();
    assertThat(errorOfRecommend()).isEqualTo(SearchErrorCode.NO_FLIGHT_FOUND);

    flightResults = List.of(flight("ZZ", "85.05", "ZZ100", LocalTime.of(14, 42)));
    assertThat(errorOfRecommend()).isEqualTo(SearchErrorCode.NO_FLIGHT_FOUND);

    assertThat(agent.recommendCalls).isZero();
  }

  @Test
  void 숙소가_없으면_AI를_부르지_않고_실패한다() {
    stayResults = List.of();
    assertThat(errorOfRecommend()).isEqualTo(SearchErrorCode.NO_STAY_FOUND);

    stayResults = List.of(stay("tax_excluded", 100_000, 9.0, 100, false));
    assertThat(errorOfRecommend()).isEqualTo(SearchErrorCode.NO_STAY_FOUND);

    assertThat(agent.recommendCalls).isZero();
  }

  @Test
  void 숙소_검색이_실패하면_그_오류를_그대로_던진다() {
    stayFailure = new GeneralException(SearchErrorCode.STAY_SEARCH_FAILED);

    assertThatThrownBy(() -> service.recommend(REQUEST))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(SearchErrorCode.STAY_SEARCH_FAILED);
    assertThat(agent.recommendCalls).isZero();
  }

  @Test
  void 출발_시간대는_Duffel에_넘기지_않고_core가_거른다() {
    agent.requirements = requirements(4.5, "afternoon");

    SearchResDTO.Recommendation result = service.recommend(REQUEST);

    SearchClientReqDTO.Flight flightRequest = flightRequests.getFirst();
    assertThat(flightRequest.originCityCode()).isEqualTo("SEL");
    assertThat(flightRequest.destinationCityCode()).isEqualTo("OSA");
    assertThat(flightRequest.departureDate()).isEqualTo(DEPARTURE);
    assertThat(flightRequest.returnDate()).isEqualTo(RETURN);
    assertThat(flightRequest.adults()).isEqualTo(2);
    assertThat(flightRequest.departureTimeFrom()).isNull();
    assertThat(flightRequest.departureTimeTo()).isNull();
    assertThat(flightRequest.maxConnections()).isEqualTo(TripRecommendationService.MAX_CONNECTIONS);

    SearchClientReqDTO.Stay stayRequest = stayRequests.getFirst();
    assertThat(stayRequest.cityName()).isEqualTo("Osaka");
    assertThat(stayRequest.countryCode()).isEqualTo("JP");
    // 후기 4.5점(5점 만점) → 리뷰 점수 9.0점(10점 만점)
    assertThat(stayRequest.minReviewScore()).isEqualTo(9.0);
    assertThat(stayRequest.limit()).isEqualTo(TripRecommendationService.STAY_LIMIT);

    // 오후 편이 없어 시간대 조건을 풀었다는 안내가 조건 변환 안내 뒤에 붙는다.
    assertThat(result.assumptions())
        .containsExactly(
            "후기 4.5점(5점 만점)을 숙소 리뷰 점수 9.0점(10점 만점) 이상으로 바꿔 적용했습니다.",
            "원하는 출발 시간대에 맞는 항공편이 없어 시간대 조건 없이 골랐습니다.");
  }
}

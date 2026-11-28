package com.trupass.core.domain.search.service;

import static com.trupass.core.domain.search.service.SearchFixtures.DEPARTURE;
import static com.trupass.core.domain.search.service.SearchFixtures.RETURN;
import static com.trupass.core.domain.search.service.SearchFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO.Requirements;
import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchCriteriaResolverTest {
  private final SearchCriteriaResolver resolver = new SearchCriteriaResolver(new CityCatalog());

  /** AI 응답 하나. 테스트마다 바꿀 값만 고친다. */
  private static final class Req {
    String origin = "인천";
    String destination = "오사카";
    LocalDate departure = DEPARTURE;
    LocalDate returnDate = RETURN;
    Integer travelers = 2;
    Long budget = 1_200_000L;
    Double rating;
    String period;
    List<String> transport;
    List<String> additionalCities;

    Requirements build() {
      return new Requirements(
          origin,
          destination,
          departure,
          returnDate,
          travelers,
          budget,
          "휴식",
          "맛집",
          "편하게",
          rating,
          List.of("역 근처"),
          transport,
          period,
          additionalCities);
    }
  }

  private SearchCriteria resolve(Req req) {
    return resolver.resolve(req.build(), null, TODAY);
  }

  private BaseErrorCode errorOf(Req req) {
    try {
      resolve(req);
    } catch (GeneralException e) {
      return e.getErrorCode();
    }
    throw new AssertionError("expected GeneralException");
  }

  @Test
  void AI_조건을_검색_조건으로_바꾼다() {
    Req req = new Req();
    req.rating = 4.0;
    req.period = "morning";

    SearchCriteria criteria = resolve(req);

    assertThat(criteria.origin().code()).isEqualTo("SEL");
    assertThat(criteria.destination().code()).isEqualTo("OSA");
    assertThat(criteria.departureDate()).isEqualTo(DEPARTURE);
    assertThat(criteria.returnDate()).isEqualTo(RETURN);
    assertThat(criteria.nights()).isEqualTo(2);
    assertThat(criteria.adults()).isEqualTo(2);
    assertThat(criteria.budgetKrw()).isEqualTo(1_200_000L);
    // 후기 평점(5점 만점) × 2 = 리뷰 점수(10점 만점)
    assertThat(criteria.minReviewScore()).isEqualTo(8.0);
    assertThat(criteria.departurePeriod()).isEqualTo("morning");
    assertThat(criteria.departureTimeFrom()).isEqualTo(LocalTime.of(6, 0));
    assertThat(criteria.departureTimeTo()).isEqualTo(LocalTime.of(12, 0));
    assertThat(criteria.accommodationPreferences()).containsExactly("역 근처");
    assertThat(criteria.assumptions())
        .containsExactly("후기 4.0점(5점 만점)을 숙소 리뷰 점수 8.0점(10점 만점) 이상으로 바꿔 적용했습니다.");
  }

  @Test
  void 평점은_소수_첫째_자리까지_반올림한다() {
    Req req = new Req();
    req.rating = 4.25;

    assertThat(resolve(req).minReviewScore()).isEqualTo(8.5);
  }

  @Test
  void 저녁_출발은_23시59분까지다() {
    Req req = new Req();
    req.period = "evening";

    SearchCriteria criteria = resolve(req);

    assertThat(criteria.departureTimeFrom()).isEqualTo(LocalTime.of(18, 0));
    assertThat(criteria.departureTimeTo()).isEqualTo(LocalTime.of(23, 59));
  }

  @Test
  void 모르는_시간대는_시간_제한_없이_검색하고_알려준다() {
    Req req = new Req();
    req.period = "dawn";

    SearchCriteria criteria = resolve(req);

    assertThat(criteria.departurePeriod()).isNull();
    assertThat(criteria.departureTimeFrom()).isNull();
    assertThat(criteria.assumptions()).containsExactly("출발 시간대 'dawn'을(를) 알 수 없어 시간 제한 없이 검색했습니다.");
  }

  @Test
  void 돌아오는_날이_없으면_2박_3일로_본다() {
    Req req = new Req();
    req.returnDate = null;

    SearchCriteria criteria = resolve(req);

    assertThat(criteria.returnDate()).isEqualTo(DEPARTURE.plusDays(2));
    assertThat(criteria.assumptions()).containsExactly("돌아오는 날을 몰라 2박 3일로 검색했습니다.");
  }

  @Test
  void 인원이_없으면_1명으로_본다() {
    Req req = new Req();
    req.travelers = null;

    SearchCriteria criteria = resolve(req);

    assertThat(criteria.adults()).isEqualTo(1);
    assertThat(criteria.assumptions()).containsExactly("인원을 몰라 1명으로 검색했습니다.");
  }

  @Test
  void 출발지가_없으면_입력한_출발_도시_그다음_서울이다() {
    Req req = new Req();
    req.origin = null;

    assertThat(resolver.resolve(req.build(), "부산", TODAY).origin().code()).isEqualTo("PUS");
    assertThat(resolver.resolve(req.build(), "부산", TODAY).assumptions()).isEmpty();

    SearchCriteria fallback = resolver.resolve(req.build(), null, TODAY);
    assertThat(fallback.origin().code()).isEqualTo("SEL");
    assertThat(fallback.assumptions()).containsExactly("출발지를 서울(SEL)로 검색했습니다.");
  }

  @Test
  void 모르는_출발지는_서울로_바꾸고_알려준다() {
    Req req = new Req();
    req.origin = "화성";

    SearchCriteria criteria = resolve(req);

    assertThat(criteria.origin().code()).isEqualTo("SEL");
    assertThat(criteria.assumptions())
        .containsExactly("출발지 '화성'을 찾지 못했습니다.", "출발지를 서울(SEL)로 바꿔 검색했습니다.");
  }

  @Test
  void AI가_출발_도시를_그대로_돌려줘도_못_찾았다는_안내는_한_번만_한다() {
    Req req = new Req();
    req.origin = "뉴욕";

    SearchCriteria criteria = resolver.resolve(req.build(), " 뉴욕 ", TODAY);

    assertThat(criteria.origin().code()).isEqualTo("SEL");
    assertThat(criteria.assumptions())
        .containsExactly("출발지 '뉴욕'을 찾지 못했습니다.", "출발지를 서울(SEL)로 바꿔 검색했습니다.");
  }

  @Test
  void 조사는_받침에_맞춘다() {
    assertThat(SearchCriteriaResolver.objectParticle("화성")).isEqualTo("을");
    assertThat(SearchCriteriaResolver.objectParticle("파리")).isEqualTo("를");
    assertThat(SearchCriteriaResolver.objectParticle("dawn")).isEqualTo("을(를)");
    assertThat(SearchCriteriaResolver.objectParticle(" ")).isEqualTo("을(를)");
  }

  @Test
  void 아직_못_하는_것은_알려준다() {
    Req req = new Req();
    req.transport = List.of("train");
    req.additionalCities = List.of("교토");

    assertThat(resolve(req).assumptions())
        .containsExactly("지금은 항공편만 검색합니다(요청한 교통수단: train).", "추가 도시(교토)는 아직 검색하지 않고 오사카만 검색했습니다.");
  }

  @Test
  void 목적지가_없거나_지원하지_않으면_실패한다() {
    Req missing = new Req();
    missing.destination = " ";
    Req unsupported = new Req();
    unsupported.destination = "화성";
    Req same = new Req();
    same.origin = "김포";
    same.destination = "서울";

    assertThat(errorOf(missing)).isEqualTo(SearchErrorCode.DESTINATION_REQUIRED);
    assertThat(errorOf(unsupported)).isEqualTo(SearchErrorCode.UNSUPPORTED_DESTINATION);
    assertThat(errorOf(same)).isEqualTo(SearchErrorCode.SAME_CITY);
  }

  @Test
  void 날짜가_없거나_틀리면_실패한다() {
    Req missing = new Req();
    missing.departure = null;
    Req past = new Req();
    past.departure = TODAY.minusDays(1);
    Req sameDay = new Req();
    sameDay.returnDate = DEPARTURE;

    assertThat(errorOf(missing)).isEqualTo(SearchErrorCode.DEPARTURE_DATE_REQUIRED);
    assertThat(errorOf(past)).isEqualTo(SearchErrorCode.INVALID_TRIP_DATES);
    assertThat(errorOf(sameDay)).isEqualTo(SearchErrorCode.INVALID_TRIP_DATES);
  }

  @Test
  void 오늘_출발은_된다() {
    Req req = new Req();
    req.departure = TODAY;
    req.returnDate = TODAY.plusDays(1);

    assertThat(resolve(req).nights()).isEqualTo(1);
  }

  @Test
  void 열_명부터는_거절한다() {
    Req nine = new Req();
    nine.travelers = 9;
    Req ten = new Req();
    ten.travelers = 10;

    assertThat(resolve(nine).adults()).isEqualTo(9);
    assertThat(errorOf(ten)).isEqualTo(SearchErrorCode.TOO_MANY_TRAVELERS);
  }
}

package com.trupass.core.domain.search.service;

import static com.trupass.core.domain.search.service.SearchFixtures.DEPARTURE;
import static com.trupass.core.domain.search.service.SearchFixtures.criteria;
import static com.trupass.core.domain.search.service.SearchFixtures.flight;
import static com.trupass.core.domain.search.service.SearchFixtures.slice;
import static com.trupass.core.domain.search.service.SearchFixtures.stay;
import static org.assertj.core.api.Assertions.assertThat;

import com.trupass.core.domain.search.client.dto.SearchClientResDTO.FlightOffer;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO.StayOffer;
import com.trupass.core.domain.search.service.CandidateSelector.FlightCandidate;
import com.trupass.core.domain.search.service.CandidateSelector.StayCandidate;
import com.trupass.core.global.common.Money;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateSelectorTest {
  private final CandidateSelector selector = new CandidateSelector(SearchFixtures.usd1400());
  private final List<String> notes = new ArrayList<>();

  // ---- 항공 ----

  @Test
  void 항공_후보는_원화로_바꿔_싼_순서로_key를_붙인다() {
    List<FlightCandidate> picked =
        selector.selectFlights(
            List.of(
                flight("JL", "470.30", "JL5150", LocalTime.of(16, 50)),
                flight("NH", "401.40", "NH862", LocalTime.of(7, 45))),
            criteria(),
            notes);

    assertThat(picked).extracting(FlightCandidate::key).containsExactly("F1", "F2");
    assertThat(picked.getFirst().offer().ownerCode()).isEqualTo("NH");
    assertThat(picked.getFirst().priceKrw()).isEqualTo(Money.krw(561_960));
    assertThat(notes).isEmpty();
  }

  @Test
  void 테스트_모드_가짜_항공사는_뺀다() {
    List<FlightCandidate> picked =
        selector.selectFlights(
            List.of(
                flight("ZZ", "85.05", "ZZ100", LocalTime.of(14, 42)),
                flight("OZ", "447.70", "OZ1145", LocalTime.of(7, 55))),
            criteria(),
            notes);

    assertThat(picked).extracting(c -> c.offer().ownerCode()).containsExactly("OZ");
  }

  @Test
  void 같은_비행기가_운임만_다르게_여러_번_오면_가장_싼_것만_남긴다() {
    List<FlightCandidate> picked =
        selector.selectFlights(
            List.of(
                flight("OZ", "500.00", "OZ1145", LocalTime.of(7, 55)),
                flight("OZ", "447.70", "OZ1145", LocalTime.of(7, 55)),
                flight("OZ", "460.00", "OZ1145", LocalTime.of(7, 55))),
            criteria(),
            notes);

    assertThat(picked).hasSize(1);
    assertThat(picked.getFirst().offer().price()).isEqualTo(new Money(44770, "USD"));
  }

  @Test
  void 항공_후보는_최대_8개다() {
    List<FlightOffer> offers = new ArrayList<>();
    for (int i = 10; i >= 1; i--) {
      offers.add(flight("NH", i + "00.00", "NH" + i, LocalTime.of(8, 0)));
    }

    List<FlightCandidate> picked = selector.selectFlights(offers, criteria(), notes);

    assertThat(picked).hasSize(CandidateSelector.MAX_FLIGHTS);
    assertThat(picked)
        .extracting(c -> c.priceKrw().amount())
        .containsExactly(
            140_000L, 280_000L, 420_000L, 560_000L, 700_000L, 840_000L, 980_000L, 1_120_000L);
  }

  @Test
  void 가격이_같으면_비행_시간이_짧은_편이_먼저다() {
    FlightOffer slow =
        new FlightOffer(
            "DUFFEL_TEST",
            "off_slow",
            "NH",
            "ANA",
            new Money(40000, "USD"),
            null,
            List.of(slice("GMP", "ITM", "NH862", DEPARTURE.atTime(7, 45), Duration.ofHours(7))));
    FlightOffer fast = flight("OZ", "400.00", "OZ1145", LocalTime.of(7, 55));

    List<FlightCandidate> picked = selector.selectFlights(List.of(slow, fast), criteria(), notes);

    assertThat(picked).extracting(c -> c.offer().ownerCode()).containsExactly("OZ", "NH");
  }

  @Test
  void 원하는_시간대의_편만_고른다() {
    List<FlightCandidate> picked =
        selector.selectFlights(
            List.of(
                flight("HR", "208.20", "7C1621", LocalTime.of(13, 35)),
                flight("NH", "401.40", "NH862", LocalTime.of(7, 45))),
            criteria(null, null, LocalTime.of(6, 0), LocalTime.of(12, 0)),
            notes);

    assertThat(picked).extracting(c -> c.offer().ownerCode()).containsExactly("NH");
    assertThat(notes).isEmpty();
  }

  @Test
  void 원하는_시간대에_편이_없으면_시간대_조건을_풀고_알려준다() {
    List<FlightCandidate> picked =
        selector.selectFlights(
            List.of(flight("HR", "208.20", "7C1621", LocalTime.of(13, 35))),
            criteria(null, null, LocalTime.of(6, 0), LocalTime.of(12, 0)),
            notes);

    assertThat(picked).hasSize(1);
    assertThat(notes).containsExactly("원하는 출발 시간대에 맞는 항공편이 없어 시간대 조건 없이 골랐습니다.");
  }

  @Test
  void 시간대는_시작을_포함하고_끝은_뺀다_단_23시59분은_포함한다() {
    LocalTime six = LocalTime.of(6, 0);
    LocalTime noon = LocalTime.of(12, 0);
    LocalTime lastMinute = LocalTime.of(23, 59);

    assertThat(CandidateSelector.departsWithin(flight("NH", "1.00", "NH1", six), six, noon))
        .isTrue();
    assertThat(CandidateSelector.departsWithin(flight("NH", "1.00", "NH1", noon), six, noon))
        .isFalse();
    assertThat(
            CandidateSelector.departsWithin(
                flight("NH", "1.00", "NH1", lastMinute), LocalTime.of(18, 0), lastMinute))
        .isTrue();
  }

  @Test
  void 비행_시간이_없는_편도는_0으로_본다() {
    FlightOffer noDuration =
        new FlightOffer(
            "DUFFEL_TEST",
            "off_x",
            "NH",
            "ANA",
            new Money(100, "USD"),
            null,
            List.of(
                slice("GMP", "KIX", "NH1", DEPARTURE.atTime(8, 0), null),
                slice(
                    "KIX",
                    "GMP",
                    "NH2",
                    DEPARTURE.plusDays(2).atTime(8, 0),
                    Duration.ofMinutes(115))));

    assertThat(CandidateSelector.totalDuration(noDuration)).isEqualTo(Duration.ofMinutes(115));
  }

  // ---- 숙소 ----

  @Test
  void 세금_별도_요금은_총액_비교가_틀어지므로_뺀다() {
    List<StayCandidate> picked =
        selector.selectStays(
            List.of(
                stay("cheap_tax_excluded", 100_000, 9.0, 100, false),
                stay("normal", 150_000, 8.7, 3340)),
            criteria(),
            notes);

    assertThat(picked).extracting(c -> c.offer().hotelId()).containsExactly("normal");
  }

  @Test
  void 리뷰가_20개_미만인_숙소는_뺀다() {
    List<StayCandidate> picked =
        selector.selectStays(
            List.of(stay("few", 100_000, 9.9, 3), stay("many", 150_000, 8.7, 3340)),
            criteria(),
            notes);

    assertThat(picked).extracting(c -> c.offer().hotelId()).containsExactly("many");
    assertThat(notes).isEmpty();
  }

  @Test
  void 리뷰가_20개_이상인_숙소가_없으면_조건을_풀고_알려준다() {
    List<StayCandidate> picked =
        selector.selectStays(
            List.of(stay("a", 100_000, 9.0, 3), stay("b", 150_000, 8.0, null)), criteria(), notes);

    assertThat(picked).hasSize(2);
    assertThat(notes).containsExactly("리뷰가 20개 이상인 숙소가 없어 리뷰 수 조건 없이 골랐습니다.");
  }

  @Test
  void 리뷰_점수_하한보다_낮은_숙소는_빼고_다_낮으면_조건을_푼다() {
    List<StayOffer> offers =
        List.of(stay("low", 100_000, 7.5, 100), stay("high", 150_000, 9.2, 100));

    assertThat(selector.selectStays(offers, criteria(null, 8.0, null, null), notes))
        .extracting(c -> c.offer().hotelId())
        .containsExactly("high");
    assertThat(notes).isEmpty();

    assertThat(selector.selectStays(offers, criteria(null, 9.5, null, null), notes)).hasSize(2);
    assertThat(notes).containsExactly("리뷰 점수 9.5점 이상인 숙소가 없어 점수 조건 없이 골랐습니다.");
  }

  @Test
  void 싼_숙소_6개에_평점_높은_숙소_4개를_더해_가격순으로_key를_붙인다() {
    // h1~h6이 가장 싸다. 나머지 중 평점 순서: h11(9.8) > h7(9.5) > h9(9.0) > h12(8.5) > h10(8.0) > h8(7.1)
    double[] scores = {7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 9.5, 7.1, 9.0, 8.0, 9.8, 8.5};
    List<StayOffer> offers = new ArrayList<>();
    for (int i = 12; i >= 1; i--) {
      offers.add(stay("h" + i, i * 100_000L, scores[i - 1], 100));
    }

    List<StayCandidate> picked = selector.selectStays(offers, criteria(), notes);

    assertThat(picked)
        .extracting(c -> c.offer().hotelId())
        .containsExactly("h1", "h2", "h3", "h4", "h5", "h6", "h7", "h9", "h11", "h12");
    assertThat(picked)
        .extracting(StayCandidate::key)
        .containsExactly("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10");
  }

  @Test
  void 숙소가_적으면_있는_만큼만_고른다() {
    List<StayCandidate> picked =
        selector.selectStays(List.of(stay("only", 388_805, 9.2, 5037)), criteria(), notes);

    assertThat(picked).hasSize(1);
    assertThat(picked.getFirst().priceKrw()).isEqualTo(Money.krw(388_805));
  }

  // ---- 함께 ----

  @Test
  void 항공과_숙소에서_푼_조건을_모아서_돌려준다() {
    CandidateSelector.Candidates candidates =
        selector.select(
            List.of(flight("HR", "208.20", "7C1621", LocalTime.of(13, 35))),
            List.of(stay("few", 100_000, 9.0, 3)),
            criteria(null, null, LocalTime.of(6, 0), LocalTime.of(12, 0)));

    assertThat(candidates.flights()).hasSize(1);
    assertThat(candidates.stays()).hasSize(1);
    assertThat(candidates.notes()).hasSize(2);
  }
}

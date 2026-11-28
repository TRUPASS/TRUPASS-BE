package com.trupass.core.domain.search.client.duffel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO;
import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.common.Money;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Duffel 테스트 모드 실제 응답(fixtures/duffel, 필드만 줄임)으로 요청 모양과 매핑을 확인한다. 외부 API는 부르지 않는다. */
class DuffelFlightSearchClientTest {
  private static final String BASE = "https://duffel.test";
  private static final String TOKEN = "duffel_test_dummy";
  private static final String OFFERS_URL =
      BASE + "/air/offers?offer_request_id=orq_test&sort=total_amount&limit=100";
  private static final String CREATED = "{\"data\":{\"id\":\"orq_test\",\"live_mode\":false}}";

  private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final DuffelFlightSearchClient client =
      new DuffelFlightSearchClient(builder.build(), TOKEN);

  private static SearchClientReqDTO.Flight request(LocalTime from, LocalTime to) {
    return new SearchClientReqDTO.Flight(
        "SEL", "OSA", LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 22), 2, from, to, 1);
  }

  private static String fixture() throws IOException {
    return new ClassPathResource("fixtures/duffel/offers_sel_osa.json")
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private void expectOfferRequest() {
    server
        .expect(requestTo(BASE + "/air/offer_requests?return_offers=false&supplier_timeout=15000"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + TOKEN))
        .andExpect(header("Duffel-Version", "v2"))
        .andRespond(
            withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CREATED));
  }

  @Test
  void 검색을_만든_뒤_싼_순서로_offer를_받아_매핑한다() throws IOException {
    server
        .expect(requestTo(BASE + "/air/offer_requests?return_offers=false&supplier_timeout=15000"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + TOKEN))
        .andExpect(header("Duffel-Version", "v2"))
        .andExpect(jsonPath("$.data.slices[0].origin").value("SEL"))
        .andExpect(jsonPath("$.data.slices[0].destination").value("OSA"))
        .andExpect(jsonPath("$.data.slices[0].departure_date").value("2026-12-20"))
        .andExpect(jsonPath("$.data.slices[0].departure_time").doesNotExist())
        .andExpect(jsonPath("$.data.slices[1].origin").value("OSA"))
        .andExpect(jsonPath("$.data.slices[1].departure_date").value("2026-12-22"))
        .andExpect(jsonPath("$.data.passengers.length()").value(2))
        .andExpect(jsonPath("$.data.passengers[0].type").value("adult"))
        .andExpect(jsonPath("$.data.max_connections").value(1))
        .andExpect(jsonPath("$.data.cabin_class").value("economy"))
        .andRespond(
            withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CREATED));
    server
        .expect(requestTo(OFFERS_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

    List<SearchClientResDTO.FlightOffer> offers = client.search(request(null, null));

    server.verify();
    // 가짜 항공사(ZZ)는 client가 거르지 않는다. 후보 고르기(CandidateSelector)에서 뺀다.
    assertThat(offers)
        .extracting(SearchClientResDTO.FlightOffer::ownerCode)
        .containsExactly("NH", "OZ", "ZZ", "HR", "JL");

    SearchClientResDTO.FlightOffer ana = offers.getFirst();
    assertThat(ana.source()).isEqualTo("DUFFEL_TEST");
    assertThat(ana.offerId()).isEqualTo("off_0355IWmAN8uOnPq92Axndl");
    assertThat(ana.ownerName()).isEqualTo("ANA");
    assertThat(ana.price()).isEqualTo(new Money(40140, "USD"));
    assertThat(ana.expiresAt()).isEqualTo(Instant.parse("2026-11-28T08:16:13.231958Z"));
    assertThat(ana.slices()).hasSize(2);

    SearchClientResDTO.FlightSlice outbound = ana.slices().getFirst();
    assertThat(outbound.originAirport()).isEqualTo("GMP");
    assertThat(outbound.destinationAirport()).isEqualTo("ITM");
    assertThat(outbound.departingAt()).isEqualTo(LocalDateTime.of(2026, 12, 20, 7, 45));
    assertThat(outbound.arrivingAt()).isEqualTo(LocalDateTime.of(2026, 12, 20, 15, 10));
    assertThat(outbound.duration()).isEqualTo(Duration.ofHours(7).plusMinutes(25));
    assertThat(outbound.stops()).isEqualTo(1);
    // 편명 앞의 0은 뗀다(0862 → 862).
    assertThat(outbound.segments())
        .extracting(SearchClientResDTO.FlightSegment::flightNumber)
        .containsExactly("NH862", "NH27");

    // 판매 항공사(owner)와 실제 편명 항공사가 다를 수 있다(Hahn Air가 제주항공 편을 판다).
    SearchClientResDTO.FlightOffer hahn = offers.get(3);
    assertThat(hahn.slices().getFirst().segments().getFirst().flightNumber()).isEqualTo("7C1621");
    assertThat(hahn.slices().getFirst().stops()).isZero();
  }

  @Test
  void 출발_시간대를_주면_가는_편에만_넣는다() {
    server
        .expect(requestTo(BASE + "/air/offer_requests?return_offers=false&supplier_timeout=15000"))
        .andExpect(jsonPath("$.data.slices[0].departure_time.from").value("06:00"))
        .andExpect(jsonPath("$.data.slices[0].departure_time.to").value("12:00"))
        .andExpect(jsonPath("$.data.slices[1].departure_time").doesNotExist())
        .andRespond(
            withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CREATED));
    server
        .expect(requestTo(OFFERS_URL))
        .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

    assertThat(client.search(request(LocalTime.of(6, 0), LocalTime.of(12, 0)))).isEmpty();
    server.verify();
  }

  @Test
  void 금액을_해석할_수_없는_offer는_버리고_나머지는_돌려준다() throws IOException {
    expectOfferRequest();
    // USD는 소수 둘째 자리까지라 401.405는 반올림하지 않고 버린다.
    String broken = fixture().replace("\"401.40\"", "\"401.405\"");
    server
        .expect(requestTo(OFFERS_URL))
        .andRespond(withSuccess(broken, MediaType.APPLICATION_JSON));

    assertThat(client.search(request(null, null)))
        .extracting(SearchClientResDTO.FlightOffer::ownerCode)
        .containsExactly("OZ", "ZZ", "HR", "JL");
  }

  @Test
  void Duffel이_오류를_주면_항공_검색_실패다() {
    server
        .expect(requestTo(BASE + "/air/offer_requests?return_offers=false&supplier_timeout=15000"))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":[{\"code\":\"validation_error\"}]}"));

    assertThatThrownBy(() -> client.search(request(null, null)))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(SearchErrorCode.FLIGHT_SEARCH_FAILED);
  }

  @Test
  void 응답이_늦으면_타임아웃이다() {
    server
        .expect(requestTo(BASE + "/air/offer_requests?return_offers=false&supplier_timeout=15000"))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    assertThatThrownBy(() -> client.search(request(null, null)))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(SearchErrorCode.SEARCH_TIMEOUT);
  }

  @Test
  void 토큰이_없으면_부르지_않고_설정_오류다() {
    RestClient.Builder unused = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer noCalls = MockRestServiceServer.bindTo(unused).build();
    DuffelFlightSearchClient unconfigured = new DuffelFlightSearchClient(unused.build(), "");

    assertThatThrownBy(() -> unconfigured.search(request(null, null)))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(SearchErrorCode.SEARCH_NOT_CONFIGURED);
    noCalls.verify();
  }

  @Test
  void 테스트_토큰이_아니면_만들_수_없다() {
    assertThatThrownBy(() -> new DuffelFlightSearchClient(RestClient.create(), "duffel_live_x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duffel_test_");
  }

  @Test
  void 편명_앞의_0을_뗀다() {
    assertThat(DuffelFlightSearchClient.stripLeadingZeros("0862")).isEqualTo("862");
    assertThat(DuffelFlightSearchClient.stripLeadingZeros("1145")).isEqualTo("1145");
    assertThat(DuffelFlightSearchClient.stripLeadingZeros("000")).isEqualTo("0");
  }
}

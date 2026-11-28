package com.trupass.core.domain.search.client.liteapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.trupass.core.domain.search.client.dto.SearchClientReqDTO;
import com.trupass.core.domain.search.client.dto.SearchClientResDTO;
import com.trupass.core.domain.search.exception.SearchErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.common.Money;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** LiteAPI 샌드박스 실제 응답(fixtures/liteapi, 호텔 3곳만 남김)으로 요청 모양과 매핑을 확인한다. */
class LiteApiStaySearchClientTest {
  private static final String BASE = "https://liteapi.test/v3.0";
  private static final String KEY = "sand_dummy";

  private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final LiteApiStaySearchClient client = new LiteApiStaySearchClient(builder.build(), KEY);

  private static SearchClientReqDTO.Stay request(Double minReviewScore) {
    return new SearchClientReqDTO.Stay(
        "Osaka",
        "JP",
        LocalDate.of(2026, 12, 20),
        LocalDate.of(2026, 12, 22),
        2,
        minReviewScore,
        20);
  }

  private static String fixture(String name) throws IOException {
    return new ClassPathResource("fixtures/liteapi/" + name)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void 요금과_호텔_정보를_호텔_id로_합친다() throws IOException {
    server
        .expect(requestTo(BASE + "/hotels/rates"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-API-Key", KEY))
        .andExpect(jsonPath("$.cityName").value("Osaka"))
        .andExpect(jsonPath("$.countryCode").value("JP"))
        .andExpect(jsonPath("$.checkin").value("2026-12-20"))
        .andExpect(jsonPath("$.checkout").value("2026-12-22"))
        .andExpect(jsonPath("$.occupancies[0].adults").value(2))
        .andExpect(jsonPath("$.currency").value("KRW"))
        .andExpect(jsonPath("$.guestNationality").value("KR"))
        .andExpect(jsonPath("$.limit").value(20))
        .andExpect(jsonPath("$.maxRatesPerHotel").value(1))
        .andExpect(jsonPath("$.minRating").value(8.0))
        .andExpect(jsonPath("$.includeHotelData").value(true))
        .andRespond(withSuccess(fixture("rates_osaka.json"), MediaType.APPLICATION_JSON));

    List<SearchClientResDTO.StayOffer> offers = client.search(request(8.0));

    server.verify();
    // 호텔 정보가 없는 요금(lp_missing)은 버린다.
    assertThat(offers)
        .extracting(SearchClientResDTO.StayOffer::hotelId)
        .containsExactly("lp65560402", "lp65579fc2", "lp1ffdc");

    SearchClientResDTO.StayOffer citadines = offers.getFirst();
    assertThat(citadines.source()).isEqualTo("LITEAPI_SANDBOX");
    assertThat(citadines.name()).isEqualTo("Citadines Namba Osaka");
    assertThat(citadines.reviewScore()).isEqualTo(9.2);
    assertThat(citadines.reviewCount()).isEqualTo(5037);
    assertThat(citadines.roomName()).isEqualTo("Deluxe Double");
    assertThat(citadines.boardType()).isEqualTo("RO");
    assertThat(citadines.boardName()).isEqualTo("Room Only");
    assertThat(citadines.refundable()).isFalse(); // NRFN
    assertThat(citadines.taxesIncluded()).isTrue();
    assertThat(citadines.price()).isEqualTo(Money.krw(388805));
    assertThat(citadines.offerId()).startsWith("AwJ2");
    assertThat(citadines.latitude()).isNotNull();

    SearchClientResDTO.StayOffer keihan = offers.get(1);
    assertThat(keihan.refundable()).isTrue(); // RFN
    assertThat(keihan.price()).isEqualTo(Money.krw(153477));
    assertThat(offers.get(2).stars()).isEqualTo(5);
  }

  @Test
  void 리뷰_점수_조건이_없으면_보내지_않는다() {
    server
        .expect(requestTo(BASE + "/hotels/rates"))
        .andExpect(jsonPath("$.minRating").doesNotExist())
        .andRespond(withSuccess("{\"data\":[],\"hotels\":[]}", MediaType.APPLICATION_JSON));

    assertThat(client.search(request(null))).isEmpty();
    server.verify();
  }

  @Test
  void 세금_별도_요금은_표시해서_돌려준다() throws IOException {
    String taxExcluded =
        fixture("rates_osaka.json").replaceFirst("\"included\": true", "\"included\": false");
    server
        .expect(requestTo(BASE + "/hotels/rates"))
        .andRespond(withSuccess(taxExcluded, MediaType.APPLICATION_JSON));

    List<SearchClientResDTO.StayOffer> offers = client.search(request(null));

    assertThat(offers.getFirst().taxesIncluded()).isFalse();
    assertThat(offers.get(1).taxesIncluded()).isTrue();
  }

  @Test
  void 빈_방_없음은_HTTP_200이어도_빈_결과다() throws IOException {
    server
        .expect(requestTo(BASE + "/hotels/rates"))
        .andRespond(withSuccess(fixture("rates_no_availability.json"), MediaType.APPLICATION_JSON));

    assertThat(client.search(request(null))).isEmpty();
  }

  @Test
  void 다른_오류_코드는_HTTP_200이어도_실패다() {
    server
        .expect(requestTo(BASE + "/hotels/rates"))
        .andRespond(
            withSuccess(
                "{\"error\":{\"code\":4002,\"message\":\"invalid\"}}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.search(request(null)))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(SearchErrorCode.STAY_SEARCH_FAILED);
  }

  @Test
  void 키가_틀리면_숙소_검색_실패다() {
    server
        .expect(requestTo(BASE + "/hotels/rates"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.search(request(null)))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(SearchErrorCode.STAY_SEARCH_FAILED);
  }

  @Test
  void 샌드박스_키가_아니면_만들_수_없다() {
    assertThatThrownBy(() -> new LiteApiStaySearchClient(RestClient.create(), "prod_x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sand_");
  }

  @Test
  void 키가_없으면_설정_오류다() {
    assertThatThrownBy(
            () -> new LiteApiStaySearchClient(RestClient.create(), "").search(request(null)))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(SearchErrorCode.SEARCH_NOT_CONFIGURED);
  }
}

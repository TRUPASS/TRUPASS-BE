package com.trupass.core.domain.trip.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.trupass.core.domain.trip.client.dto.AgentClientReqDTO;
import com.trupass.core.domain.trip.client.dto.AgentClientResDTO;
import com.trupass.core.domain.trip.exception.TripErrorCode;
import com.trupass.core.global.apiPayload.code.BaseErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** agent와 주고받는 JSON 모양(snake_case)과 오류 변환을 확인한다. agent 서버는 띄우지 않는다. */
class HttpAgentClientTest {
  private static final String BASE = "http://agent.test";
  private static final String KEY = "internal-test-key";

  private static final String PLAN_RESPONSE =
      """
      {
        "prompt_id": "prm_1",
        "requirements": {
          "origin": "서울",
          "destination": "오사카",
          "departure_date": "2026-12-20",
          "return_date": "2026-12-22",
          "travelers": 2,
          "budget_krw": 1200000,
          "min_traveler_rating": 4.0,
          "accommodation_preferences": ["역 근처"],
          "departure_period": "morning",
          "unknown_field": "무시한다"
        },
        "gemini_called": true,
        "prompt_tokens": 812,
        "output_tokens": 95
      }
      """;

  private static final String RECOMMEND_RESPONSE =
      """
      {
        "summary": "오사카 2박 조합입니다.",
        "options": [
          {"flight_key": "F1", "stay_key": "S2", "title": "가성비", "reason": "가장 싸다"}
        ],
        "gemini_called": true,
        "prompt_tokens": 1500,
        "output_tokens": 210
      }
      """;

  private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final HttpAgentClient client = new HttpAgentClient(builder.build(), KEY);

  private static AgentClientReqDTO.Plan planRequest() {
    return new AgentClientReqDTO.Plan("12월 20일 오사카 2박", "역 근처", "맛집", null);
  }

  private static AgentClientReqDTO.Recommend recommendRequest() {
    var trip =
        new AgentClientReqDTO.Trip(
            "서울(SEL)",
            "오사카(OSA)",
            "2026-12-20",
            "2026-12-22",
            2,
            2,
            null,
            "휴식",
            null,
            null,
            List.of("역 근처"),
            null);
    var outbound =
        new AgentClientReqDTO.FlightLeg(
            "GMP", "KIX", "2026-12-20T07:45", "2026-12-20T09:35", 110L, 0, List.of("NH862"));
    var flight = new AgentClientReqDTO.FlightCandidate("F1", "ANA", 561_960, outbound, null);
    var stay =
        new AgentClientReqDTO.StayCandidate(
            "S1", "Citadines", 4, 9.2, 5037, 388_805, "Studio", "Room Only", false, null);
    return new AgentClientReqDTO.Recommend(trip, List.of(flight), List.of(stay), 3);
  }

  private static BaseErrorCode errorOf(ThrowingCallable call) {
    try {
      call.call();
    } catch (GeneralException e) {
      return e.getErrorCode();
    } catch (Throwable e) {
      throw new AssertionError("expected GeneralException", e);
    }
    throw new AssertionError("expected GeneralException");
  }

  @Test
  void plan은_내부_키를_붙여_snake_case로_보내고_응답을_읽는다() {
    server
        .expect(requestTo(BASE + "/api/v1/trips/plan"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpAgentClient.INTERNAL_KEY_HEADER, KEY))
        .andExpect(jsonPath("$.prompt").value("12월 20일 오사카 2박"))
        .andExpect(jsonPath("$.accommodation_prompt").value("역 근처"))
        .andExpect(jsonPath("$.purpose_prompt").value("맛집"))
        .andExpect(jsonPath("$.origin_hint").doesNotExist())
        .andRespond(withSuccess(PLAN_RESPONSE, MediaType.APPLICATION_JSON));

    AgentClientResDTO.Plan plan = client.plan(planRequest());

    server.verify();
    assertThat(plan.promptId()).isEqualTo("prm_1");
    assertThat(plan.geminiCalled()).isTrue();
    assertThat(plan.promptTokens()).isEqualTo(812);
    AgentClientResDTO.Requirements r = plan.requirements();
    assertThat(r.destination()).isEqualTo("오사카");
    assertThat(r.departureDate()).isEqualTo(LocalDate.of(2026, 12, 20));
    assertThat(r.returnDate()).isEqualTo(LocalDate.of(2026, 12, 22));
    assertThat(r.travelers()).isEqualTo(2);
    assertThat(r.budgetKrw()).isEqualTo(1_200_000L);
    assertThat(r.minTravelerRating()).isEqualTo(4.0);
    assertThat(r.accommodationPreferences()).containsExactly("역 근처");
    assertThat(r.departurePeriod()).isEqualTo("morning");
    // agent가 보내지 않은 필드는 null
    assertThat(r.transportPreferences()).isNull();
  }

  @Test
  void recommend는_후보를_snake_case로_보내고_고른_key를_읽는다() {
    server
        .expect(requestTo(BASE + "/api/v1/trips/recommend"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpAgentClient.INTERNAL_KEY_HEADER, KEY))
        .andExpect(jsonPath("$.max_options").value(3))
        .andExpect(jsonPath("$.trip.origin").value("서울(SEL)"))
        .andExpect(jsonPath("$.trip.departure_date").value("2026-12-20"))
        .andExpect(jsonPath("$.trip.budget_krw").doesNotExist())
        .andExpect(jsonPath("$.trip.accommodation_preferences[0]").value("역 근처"))
        .andExpect(jsonPath("$.flights[0].key").value("F1"))
        .andExpect(jsonPath("$.flights[0].price_krw").value(561960))
        .andExpect(jsonPath("$.flights[0].outbound.from").value("GMP"))
        .andExpect(jsonPath("$.flights[0].outbound.departing_at").value("2026-12-20T07:45"))
        .andExpect(jsonPath("$.flights[0].outbound.duration_minutes").value(110))
        .andExpect(jsonPath("$.flights[0].outbound.flight_numbers[0]").value("NH862"))
        // 편도면 inbound는 null로 보낸다(agent 모델이 Optional).
        .andExpect(jsonPath("$.flights[0].inbound").isEmpty())
        .andExpect(jsonPath("$.stays[0].review_score").value(9.2))
        .andExpect(jsonPath("$.stays[0].price_krw").value(388805))
        .andExpect(jsonPath("$.stays[0].refundable").value(false))
        .andExpect(jsonPath("$.stays[0].address").doesNotExist())
        .andRespond(withSuccess(RECOMMEND_RESPONSE, MediaType.APPLICATION_JSON));

    AgentClientResDTO.Recommend recommend = client.recommend(recommendRequest());

    server.verify();
    assertThat(recommend.summary()).isEqualTo("오사카 2박 조합입니다.");
    assertThat(recommend.options())
        .containsExactly(new AgentClientResDTO.Option("F1", "S2", "가성비", "가장 싸다"));
    assertThat(recommend.outputTokens()).isEqualTo(210);
  }

  @Test
  void 조건이나_조합이_비어_오면_실패한다() {
    server
        .expect(requestTo(BASE + "/api/v1/trips/plan"))
        .andRespond(withSuccess("{\"prompt_id\":\"prm_1\"}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(BASE + "/api/v1/trips/recommend"))
        .andRespond(withSuccess("{\"summary\":\"\",\"options\":[]}", MediaType.APPLICATION_JSON));

    assertThat(errorOf(() -> client.plan(planRequest()))).isEqualTo(TripErrorCode.INVALID_PACKAGE);
    assertThat(errorOf(() -> client.recommend(recommendRequest())))
        .isEqualTo(TripErrorCode.INVALID_PACKAGE);
  }

  @Test
  void agent가_오류를_주면_연결_불가로_바꾼다() {
    server
        .expect(requestTo(BASE + "/api/v1/trips/plan"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server
        .expect(requestTo(BASE + "/api/v1/trips/plan"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThat(errorOf(() -> client.plan(planRequest())))
        .isEqualTo(TripErrorCode.AGENT_UNAVAILABLE);
    assertThat(errorOf(() -> client.plan(planRequest())))
        .isEqualTo(TripErrorCode.AGENT_UNAVAILABLE);
  }

  @Test
  void 응답이_늦으면_시간_초과로_바꾼다() {
    server
        .expect(requestTo(BASE + "/api/v1/trips/recommend"))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    assertThatThrownBy(() -> client.recommend(recommendRequest()))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(TripErrorCode.AGENT_TIMEOUT);
  }

  @Test
  void 내부_키가_비어_있으면_헤더를_보내지_않는다() {
    RestClient.Builder noKeyBuilder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer noKeyServer = MockRestServiceServer.bindTo(noKeyBuilder).build();
    HttpAgentClient noKeyClient = new HttpAgentClient(noKeyBuilder.build(), " ");
    noKeyServer
        .expect(requestTo(BASE + "/api/v1/trips/plan"))
        .andExpect(headerDoesNotExist(HttpAgentClient.INTERNAL_KEY_HEADER))
        .andRespond(withSuccess(PLAN_RESPONSE, MediaType.APPLICATION_JSON));

    noKeyClient.plan(planRequest());

    noKeyServer.verify();
  }
}

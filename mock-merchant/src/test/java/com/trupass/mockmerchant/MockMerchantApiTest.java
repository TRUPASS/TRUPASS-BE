package com.trupass.mockmerchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MockMerchantApiTest {
  private static final String DEFAULT_OFFER = "A-ICN-KIX-001";

  @Autowired MockMvc mockMvc;

  @Autowired MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.set(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void 판매자별_오퍼를_조회한다() throws Exception {
    mockMvc
        .perform(get("/merchants/A/offers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].merchantId").value("A"));
    mockMvc.perform(get("/merchants/Z/offers")).andExpect(status().isNotFound());
  }

  @Test
  void X_Mock_Fail_SOLD_OUT으로_hold를_요청하면_409가_온다() throws Exception {
    hold(null, "SOLD_OUT", DEFAULT_OFFER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SOLD_OUT"));
  }

  @Test
  void 같은_Idempotency_Key로_두_번_hold하면_같은_응답이_온다() throws Exception {
    String key = newKey();
    String first =
        hold(key).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String second =
        hold(key)
            .andExpect(status().isCreated())
            .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(second).isEqualTo(first);

    String otherKey = hold(newKey()).andReturn().getResponse().getContentAsString();
    assertThat(otherKey).as("다른 키는 새 hold를 만든다").isNotEqualTo(first);
  }

  @Test
  void 첫_응답이_SOLD_OUT이면_같은_키_재요청도_SOLD_OUT을_재생한다() throws Exception {
    String key = newKey();
    hold(key, "SOLD_OUT", DEFAULT_OFFER).andExpect(status().isConflict());
    hold(key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOLD_OUT"));
  }

  @Test
  void _5xx는_저장하지_않아_같은_키로_재시도하면_성공한다() throws Exception {
    String key = newKey();
    hold(key, "ERROR_500", DEFAULT_OFFER).andExpect(status().isInternalServerError());
    hold(key).andExpect(status().isCreated());
  }

  @Test
  void 같은_키를_다른_요청에_쓰면_422() throws Exception {
    String key = newKey();
    hold(key, null, "A-ICN-KIX-001").andExpect(status().isCreated());
    hold(key, null, "A-ICN-FUK-001")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void hold_pay_confirm_cancel_정상_흐름() throws Exception {
    String holdId = extract(hold(newKey()), "holdId");
    mockMvc
        .perform(payRequest(holdId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
    String bookingId = extract(mockMvc.perform(post("/holds/" + holdId + "/confirm")), "bookingId");
    mockMvc
        .perform(post("/bookings/" + bookingId + "/cancel"))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void hold는_10분_뒤_만료되어_pay할_수_없고_release는_여전히_성공한다() throws Exception {
    String holdId = extract(hold(newKey()), "holdId");
    clock.set(clock.instant().plus(Duration.ofMinutes(10)));

    mockMvc
        .perform(payRequest(holdId))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    mockMvc
        .perform(post("/holds/" + holdId + "/release"))
        .andExpect(jsonPath("$.status").value("RELEASED"));
  }

  private ResultActions hold(String key) throws Exception {
    return hold(key, null, DEFAULT_OFFER);
  }

  private ResultActions hold(String key, String fail, String offerId) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/merchants/A/holds")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"offerId\":\"" + offerId + "\",\"quantity\":1}");
    if (key != null) {
      request.header(IdempotencyFilter.HEADER, key);
    }
    if (fail != null) {
      request.header(FailureInjectionFilter.FAIL_HEADER, fail);
    }
    return mockMvc.perform(request);
  }

  private static MockHttpServletRequestBuilder payRequest(String holdId) {
    return post("/holds/" + holdId + "/pay")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"amount\":320000,\"currency\":\"KRW\"}");
  }

  private static String extract(ResultActions result, String field) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString();
    Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(body);
    assertThat(matcher.find()).as("%s in %s", field, body).isTrue();
    return matcher.group(1);
  }

  private static String newKey() {
    return "co_" + UUID.randomUUID() + ":A:HOLD";
  }

  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }
}

package com.trupass.core.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trupass.core.domain.auth.client.GoogleAuthClient;
import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;
import com.trupass.core.domain.user.dto.UserResDTO;
import com.trupass.core.domain.user.entity.enums.TravelInterest;
import com.trupass.core.domain.user.service.UserService;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 회원 탈퇴 · 약관 동의 이력 · 추천용 성향 조회. Google 검증만 가짜로 바꾸고 실제 DB를 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserAccountFlowTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:0.8.7-pg18")
              .asCompatibleSubstituteFor("postgres"));

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserService userService;

  @MockitoBean GoogleAuthClient googleAuthClient;

  private static String profile(boolean marketing) {
    return """
    {"nickname":"도쿄여행자","birthYear":1994,"gender":"FEMALE","interests":["NATURE","FOOD"],
     "agreements":{"termsOfService":true,"privacyCollection":true,"marketing":%s}}
    """
        .formatted(marketing);
  }

  @Test
  void 탈퇴하면_개인정보를_지우고_토큰을_막으며_같은_Google_계정은_새로_가입한다() throws Exception {
    String login = googleLogin("sub-withdraw").andReturn().getResponse().getContentAsString();
    String accessToken = JsonPath.read(login, "$.result.tokens.accessToken");
    String refreshToken = JsonPath.read(login, "$.result.tokens.refreshToken");
    Integer userId = JsonPath.read(login, "$.result.user.userId");
    putProfile(accessToken, profile(true)).andExpect(status().isOk());

    mockMvc
        .perform(delete("/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true));

    // 개인정보는 지우고 행은 남긴다
    Map<String, Object> row = jdbc.queryForMap("select * from users where id = ?", userId);
    assertThat(row.get("google_sub")).isEqualTo("withdrawn:" + userId);
    assertThat(row.get("email")).isEqualTo("withdrawn-" + userId + "@deleted.invalid");
    assertThat(row)
        .containsEntry("name", null)
        .containsEntry("picture_url", null)
        .containsEntry("nickname", null)
        .containsEntry("birth_year", null)
        .containsEntry("gender", null);
    assertThat(row.get("withdrawn_at")).isNotNull();
    assertThat(count("user_interests", userId)).isZero();
    assertThat(count("user_agreements", userId)).isZero();

    // 남아 있는 access token으로 오는 요청은 거절한다 (401이라 FE는 재발급을 시도한다)
    mockMvc
        .perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER401_1"));
    putProfile(accessToken, profile(false))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER401_1"));
    mockMvc
        .perform(delete("/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USER401_1"));
    // refresh token은 탈퇴와 함께 모두 폐기됐다
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH401_3"));

    // 같은 Google 계정으로 다시 오면 새 회원으로 가입한다
    googleLogin("sub-withdraw")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.newUser").value(true))
        .andExpect(jsonPath("$.result.user.userId").value(not(userId)))
        .andExpect(jsonPath("$.result.user.email").value("sub-withdraw@example.com"))
        .andExpect(jsonPath("$.result.user.profileCompleted").value(false));
  }

  @Test
  void 약관_동의와_철회를_바뀔_때만_이력에_쌓고_탈퇴하면_모두_거둔_것으로_남긴다() throws Exception {
    String accessToken = accessToken("sub-history");
    Integer userId = userId(accessToken);

    putProfile(accessToken, profile(false))
        .andExpect(jsonPath("$.result.agreements.marketing").value(false))
        .andExpect(jsonPath("$.result.agreements.marketingAgreedAt").isEmpty());
    putProfile(accessToken, profile(false)); // 같은 값: 이력이 늘지 않는다
    putProfile(accessToken, profile(true))
        .andExpect(jsonPath("$.result.agreements.marketing").value(true))
        .andExpect(jsonPath("$.result.agreements.marketingAgreedAt").isNotEmpty());
    putProfile(accessToken, profile(true)); // 같은 값
    putProfile(accessToken, profile(false))
        .andExpect(jsonPath("$.result.agreements.marketingAgreedAt").isEmpty());
    mockMvc.perform(delete("/users/me").header("Authorization", "Bearer " + accessToken));

    assertThat(history(userId))
        .containsExactly(
            "TERMS_OF_SERVICE:true",
            "PRIVACY_COLLECTION:true",
            "MARKETING:true",
            "MARKETING:false",
            "TERMS_OF_SERVICE:false",
            "PRIVACY_COLLECTION:false");
  }

  @Test
  void 추천용_성향은_연령대와_관심사만_주고_회원_정보_입력_전에는_비어_있다() throws Exception {
    String accessToken = accessToken("sub-preference");
    long userId = userId(accessToken);

    assertThat(userService.getTravelPreference(userId))
        .isEqualTo(new UserResDTO.TravelPreference(null, List.of()));

    int birthYear = Year.now(ZoneOffset.UTC).getValue() - 34;
    putProfile(accessToken, profile(false).replace("1994", String.valueOf(birthYear)))
        .andExpect(status().isOk());
    assertThat(userService.getTravelPreference(userId))
        .isEqualTo(
            new UserResDTO.TravelPreference(
                30, List.of(TravelInterest.FOOD, TravelInterest.NATURE)));

    mockMvc.perform(delete("/users/me").header("Authorization", "Bearer " + accessToken));
    assertThatThrownBy(() -> userService.getTravelPreference(userId))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode().getCode())
        .isEqualTo("USER401_1");
  }

  private List<String> history(Integer userId) {
    return jdbc.queryForList(
        "select terms_type || ':' || agreed from user_agreement_histories where user_id = ? order by id",
        String.class,
        userId);
  }

  private int count(String table, Integer userId) {
    Integer n =
        jdbc.queryForObject(
            "select count(*) from " + table + " where user_id = ?", Integer.class, userId);
    return n == null ? 0 : n;
  }

  private Integer userId(String accessToken) throws Exception {
    String me =
        mockMvc
            .perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(me, "$.result.userId");
  }

  private String accessToken(String sub) throws Exception {
    String login = googleLogin(sub).andReturn().getResponse().getContentAsString();
    return JsonPath.read(login, "$.result.tokens.accessToken");
  }

  private ResultActions googleLogin(String sub) throws Exception {
    given(googleAuthClient.verifyIdToken(sub))
        .willReturn(
            new GoogleAuthClientResDTO.IdToken(sub, sub + "@example.com", true, "여행자", null));
    return mockMvc.perform(
        post("/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"" + sub + "\"}"));
  }

  private ResultActions putProfile(String accessToken, String body) throws Exception {
    return mockMvc.perform(
        put("/users/me/profile")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}

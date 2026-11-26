package com.trupass.core.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.trupass.core.domain.auth.client.GoogleAuthClient;
import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * FE 흐름 그대로: Google 로그인 → 회원 정보 입력(PUT /users/me/profile) → 다시 로그인. Google 검증만 가짜로 바꾸고 실제 DB를 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserProfileFlowTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:0.8.7-pg18")
              .asCompatibleSubstituteFor("postgres"));

  /** FE 회원 정보 입력 화면에서 보내는 값 (닉네임 앞뒤 공백은 서버가 지운다) */
  private static final String SIGNUP =
      """
      {"nickname":"  도쿄여행자  ","birthYear":1994,"gender":"FEMALE",
       "interests":["ARCHITECTURE","CULTURE"],
       "agreements":{"termsOfService":true,"privacyCollection":true,"marketing":true}}
      """;

  @Autowired MockMvc mockMvc;

  @MockitoBean GoogleAuthClient googleAuthClient;

  @Test
  void 처음_로그인하면_회원_정보_입력_전이고_입력을_마치면_다시_로그인해도_완료로_나온다() throws Exception {
    String login =
        googleLogin("sub-signup")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.newUser").value(true))
            .andExpect(jsonPath("$.result.user.profileCompleted").value(false))
            .andExpect(jsonPath("$.result.user.nickname").isEmpty())
            .andExpect(jsonPath("$.result.user.interests").value(empty()))
            .andExpect(jsonPath("$.result.user.agreements.termsOfService").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = JsonPath.read(login, "$.result.tokens.accessToken");

    putProfile(accessToken, SIGNUP)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.result.email").value("sub-signup@example.com"))
        .andExpect(jsonPath("$.result.nickname").value("도쿄여행자"))
        .andExpect(jsonPath("$.result.birthYear").value(1994))
        .andExpect(jsonPath("$.result.gender").value("FEMALE"))
        .andExpect(jsonPath("$.result.interests").value(contains("CULTURE", "ARCHITECTURE")))
        .andExpect(jsonPath("$.result.agreements.termsOfService").value(true))
        .andExpect(jsonPath("$.result.agreements.privacyCollection").value(true))
        .andExpect(jsonPath("$.result.agreements.marketing").value(true))
        .andExpect(jsonPath("$.result.profileCompleted").value(true));

    // 앱을 다시 열면 FE가 GET /users/me로 확인한다
    me(accessToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.nickname").value("도쿄여행자"))
        .andExpect(jsonPath("$.result.profileCompleted").value(true));

    // 다시 로그인하면 회원 정보 입력을 건너뛴다
    googleLogin("sub-signup")
        .andExpect(jsonPath("$.result.newUser").value(false))
        .andExpect(jsonPath("$.result.user.profileCompleted").value(true))
        .andExpect(jsonPath("$.result.user.nickname").value("도쿄여행자"));
  }

  @Test
  void 회원_정보_입력을_마치지_않고_나갔다가_다시_로그인해도_입력_전으로_나온다() throws Exception {
    googleLogin("sub-left").andExpect(jsonPath("$.result.newUser").value(true));

    // newUser는 처음 한 번만 true라서, FE는 profileCompleted로 회원 정보 입력에 보낼지 정한다
    googleLogin("sub-left")
        .andExpect(jsonPath("$.result.newUser").value(false))
        .andExpect(jsonPath("$.result.user.profileCompleted").value(false));
  }

  @Test
  void 설정에서_고치면_새_값으로_바뀌고_선택_약관은_거둘_수_있다() throws Exception {
    String accessToken = accessToken("sub-edit");
    putProfile(accessToken, SIGNUP).andExpect(status().isOk());

    putProfile(
            accessToken,
            """
            {"nickname":"오사카","birthYear":1990,"gender":null,"interests":[],
             "agreements":{"termsOfService":true,"privacyCollection":true,"marketing":false}}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.nickname").value("오사카"))
        .andExpect(jsonPath("$.result.birthYear").value(1990))
        .andExpect(jsonPath("$.result.gender").isEmpty())
        .andExpect(jsonPath("$.result.interests").value(empty()))
        .andExpect(jsonPath("$.result.agreements.termsOfService").value(true))
        .andExpect(jsonPath("$.result.agreements.marketing").value(false))
        .andExpect(jsonPath("$.result.profileCompleted").value(true));

    // 성별 · 관심사 · 선택 약관은 빠뜨려도 된다. 닉네임은 12자까지 된다
    putProfile(
            accessToken,
            """
            {"nickname":"가나다라마바사아자차카타","birthYear":1990,
             "agreements":{"termsOfService":true,"privacyCollection":true}}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.nickname").value("가나다라마바사아자차카타"))
        .andExpect(jsonPath("$.result.interests").value(empty()))
        .andExpect(jsonPath("$.result.agreements.marketing").value(false));
  }

  @Test
  void 칸이_틀리면_칸별_메시지와_함께_400이고_저장하지_않는다() throws Exception {
    String accessToken = accessToken("sub-invalid");

    invalid(accessToken, SIGNUP.replace("  도쿄여행자  ", "a"), "nickname");
    invalid(accessToken, SIGNUP.replace("  도쿄여행자  ", "   a   "), "nickname");
    invalid(accessToken, SIGNUP.replace("  도쿄여행자  ", "가나다라마바사아자차카타파"), "nickname");
    invalid(accessToken, SIGNUP.replace("  도쿄여행자  ", "도쿄\\n여행자"), "nickname");
    invalid(accessToken, SIGNUP.replace("1994", "1899"), "birthYear");
    invalid(accessToken, SIGNUP.replace("\"birthYear\":1994,", ""), "birthYear");
    invalid(
        accessToken,
        SIGNUP.replace(
            "[\"ARCHITECTURE\",\"CULTURE\"]", "[\"FOOD\",\"SHOPPING\",\"NATURE\",\"CULTURE\"]"),
        "interests");
    invalid(
        accessToken,
        SIGNUP.replace("\"termsOfService\":true", "\"termsOfService\":false"),
        "agreements.termsOfService");
    invalid(
        accessToken,
        SIGNUP.replace("\"privacyCollection\":true,", ""),
        "agreements.privacyCollection");
    invalid(
        accessToken,
        """
        {"nickname":"도쿄여행자","birthYear":1994,"interests":[]}
        """,
        "agreements");

    // 출생 연도가 올해보다 뒤
    int nextYear = Year.now(ZoneOffset.UTC).getValue() + 1;
    putProfile(accessToken, SIGNUP.replace("1994", String.valueOf(nextYear)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER400_1"));

    // 없는 관심사 · 성별 값
    putProfile(accessToken, SIGNUP.replace("\"CULTURE\"", "\"SKIING\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false));
    putProfile(accessToken, SIGNUP.replace("\"FEMALE\"", "\"여성\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false));

    me(accessToken).andExpect(jsonPath("$.result.profileCompleted").value(false));
  }

  @Test
  void access_token이_없으면_401() throws Exception {
    mockMvc
        .perform(put("/users/me/profile").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON401"));
  }

  @Test
  void FE_개발_서버에서_PUT_preflight가_통과한다() throws Exception {
    mockMvc
        .perform(
            options("/users/me/profile")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")));
  }

  /**
   * FE는 /v3/api-docs로 타입을 만든다(pnpm gen:api --remote). 로그인 사용자 ID가 요청 파라미터로 새거나, 요청·응답 스키마 이름이 겹쳐
   * 한쪽이 덮이면 FE 타입이 틀어진다.
   */
  @Test
  void OpenAPI_스펙이_FE_타입_생성에_맞다() throws Exception {
    String spec =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    DocumentContext doc =
        JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS))
            .parse(spec);

    List<String> meParams = doc.read("$.paths['/users/me'].get.parameters[*].name");
    List<String> profileParams = doc.read("$.paths['/users/me/profile'].put.parameters[*].name");
    List<String> withdrawParams = doc.read("$.paths['/users/me'].delete.parameters[*].name");
    assertThat(meParams).doesNotContain("userId");
    assertThat(profileParams).doesNotContain("userId");
    assertThat(withdrawParams).doesNotContain("userId");
    Map<String, Object> withdrawOp = doc.read("$.paths['/users/me'].delete");
    assertThat(withdrawOp).isNotNull();

    List<String> profileRequired = doc.read("$.components.schemas.Profile.required");
    assertThat(profileRequired).containsExactlyInAnyOrder("nickname", "birthYear", "agreements");
    List<String> agreementsRequired = doc.read("$.components.schemas.Agreements.required");
    assertThat(agreementsRequired).containsExactlyInAnyOrder("termsOfService", "privacyCollection");
    Map<String, Object> statusProps = doc.read("$.components.schemas.AgreementStatus.properties");
    assertThat(statusProps)
        .containsOnlyKeys("termsOfService", "privacyCollection", "marketing", "marketingAgreedAt");
    Map<String, Object> meProps = doc.read("$.components.schemas.Me.properties");
    assertThat(meProps).containsKeys("nickname", "interests", "agreements", "profileCompleted");
  }

  private void invalid(String accessToken, String body, String field) throws Exception {
    putProfile(accessToken, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400_1"))
        .andExpect(jsonPath("$.result['" + field + "']").isNotEmpty());
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

  private ResultActions me(String accessToken) throws Exception {
    return mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken));
  }
}

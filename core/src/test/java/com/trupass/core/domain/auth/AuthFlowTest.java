package com.trupass.core.domain.auth;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trupass.core.domain.auth.client.GoogleAuthClient;
import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;
import com.trupass.core.domain.auth.exception.AuthErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
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

/** Google 검증만 가짜로 바꾸고, 가입 → 토큰 발급 → 내 정보 → 재발급 → 로그아웃을 실제 DB로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:0.8.7-pg18")
              .asCompatibleSubstituteFor("postgres"));

  @Autowired MockMvc mockMvc;

  @MockitoBean GoogleAuthClient googleAuthClient;

  @Test
  void Google_로그인부터_로그아웃까지() throws Exception {
    given(googleAuthClient.verifyIdToken("google-id-token"))
        .willReturn(
            new GoogleAuthClientResDTO.IdToken(
                "google-sub-1", "traveler@example.com", true, "여행자", null));

    // 처음 로그인 → 가입
    String login =
        googleLogin("google-id-token")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.result.newUser").value(true))
            .andExpect(jsonPath("$.result.user.email").value("traveler@example.com"))
            .andExpect(jsonPath("$.result.tokens.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = JsonPath.read(login, "$.result.tokens.accessToken");
    String refreshToken = JsonPath.read(login, "$.result.tokens.refreshToken");
    Integer userId = JsonPath.read(login, "$.result.user.userId");

    // 두 번째 로그인 → 같은 사용자
    googleLogin("google-id-token")
        .andExpect(jsonPath("$.result.newUser").value(false))
        .andExpect(jsonPath("$.result.user.userId").value(userId));

    // access token으로 내 정보
    mockMvc
        .perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.userId").value(userId))
        .andExpect(jsonPath("$.result.name").value("여행자"));

    // refresh → 새 토큰 쌍. 쓴 refresh token은 다시 못 쓴다.
    String refreshed =
        refresh(refreshToken)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefreshToken = JsonPath.read(refreshed, "$.result.refreshToken");
    refresh(refreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH401_3"));

    // 재사용이 감지되면 그 사용자의 refresh token이 모두 폐기된다.
    refresh(newRefreshToken).andExpect(status().isUnauthorized());

    // 로그아웃 후에는 refresh 불가
    String again = googleLogin("google-id-token").andReturn().getResponse().getContentAsString();
    String lastRefreshToken = JsonPath.read(again, "$.result.tokens.refreshToken");
    mockMvc
        .perform(
            post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + lastRefreshToken + "\"}"))
        .andExpect(status().isOk());
    refresh(lastRefreshToken).andExpect(status().isUnauthorized());
  }

  @Test
  void 이메일이_인증되지_않은_Google_계정은_거부한다() throws Exception {
    given(googleAuthClient.verifyIdToken("unverified"))
        .willReturn(
            new GoogleAuthClientResDTO.IdToken("google-sub-2", "x@example.com", false, null, null));

    googleLogin("unverified")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH401_2"));
  }

  @Test
  void Google_검증에_실패하면_401() throws Exception {
    given(googleAuthClient.verifyIdToken("bad"))
        .willThrow(new GeneralException(AuthErrorCode.INVALID_GOOGLE_TOKEN));

    googleLogin("bad")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH401_1"));
  }

  @Test
  void ID_토큰이_비어_있으면_400() throws Exception {
    googleLogin("")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.result.idToken").exists());
  }

  @Test
  void access_token이_없거나_틀리면_401() throws Exception {
    mockMvc
        .perform(get("/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON401"));
    mockMvc
        .perform(get("/users/me").header("Authorization", "Bearer not-a-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON401_1"));
  }

  @Test
  void 인증이_필요한_경로도_CORS_preflight는_통과한다() throws Exception {
    mockMvc
        .perform(
            options("/users/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk());
  }

  private ResultActions googleLogin(String idToken) throws Exception {
    return mockMvc.perform(
        post("/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"" + idToken + "\"}"));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mockMvc.perform(
        post("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
  }
}

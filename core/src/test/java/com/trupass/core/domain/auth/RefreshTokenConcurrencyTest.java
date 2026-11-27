package com.trupass.core.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trupass.core.domain.auth.client.GoogleAuthClient;
import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;
import com.trupass.core.domain.auth.dto.AuthReqDTO;
import com.trupass.core.domain.auth.dto.AuthResDTO;
import com.trupass.core.domain.auth.exception.AuthErrorCode;
import com.trupass.core.domain.auth.service.AuthService;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.security.JwtTokenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 같은 refresh token으로 재발급이 동시에 들어오는 경우를 실제 PostgreSQL로 확인한다(#17).
 *
 * <p>한 건만 성공하고, 나머지는 이미 쓴 토큰의 재사용(탈취)으로 처리돼 그 사용자의 refresh token이 모두 폐기돼야 한다. 성공한 요청이 받은 새 토큰 쌍도
 * 예외가 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RefreshTokenConcurrencyTest {

  private static final int THREADS = 10;

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:0.8.7-pg18")
              .asCompatibleSubstituteFor("postgres"));

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:8.12.0-alpine")).withExposedPorts(6379);

  @Autowired MockMvc mockMvc;
  @Autowired AuthService authService;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired StringRedisTemplate redisTemplate;

  @MockitoBean GoogleAuthClient googleAuthClient;
  @MockitoSpyBean JwtTokenProvider jwtTokenProvider;

  @Test
  void 같은_refresh_token으로_동시에_재발급하면_한_건만_성공하고_모든_토큰이_폐기된다() throws Exception {
    LoggedIn user = loginAs("google-sub-race");

    Outcomes outcomes = refreshConcurrently(user.refreshToken());

    assertOneWinnerAndEverythingRevoked(outcomes, user.userId());
  }

  @Test
  void 이긴_요청의_발급이_느려도_그_요청이_받은_토큰까지_폐기된다() throws Exception {
    LoggedIn user = loginAs("google-sub-race-slow");

    // 이긴 요청의 소비와 발급 사이를 일부러 벌린다. 둘이 따로 커밋되면 진 요청의 일괄 폐기가 이 틈에 먼저 실행돼, 그 뒤에 저장된 새 refresh
    // token이 폐기를 피해 살아남는다. 같은 트랜잭션이면 진 요청은 발급까지 커밋될 때까지 락에서 기다린다.
    willAnswer(
            invocation -> {
              Thread.sleep(300);
              return invocation.callRealMethod();
            })
        .given(jwtTokenProvider)
        .createAccessToken(anyLong());

    Outcomes outcomes = refreshConcurrently(user.refreshToken());

    assertOneWinnerAndEverythingRevoked(outcomes, user.userId());
  }

  private void assertOneWinnerAndEverythingRevoked(Outcomes outcomes, long userId)
      throws Exception {
    // 실패 메시지에 토큰 원문이 찍히지 않게 성공은 개수로 비교한다
    assertThat(outcomes.successes().size()).as("성공한 재발급 수").isEqualTo(1);
    assertThat(outcomes.failures())
        .as("실패한 재발급")
        .hasSize(THREADS - 1)
        .allSatisfy(e ->
            assertThat(e)
                .isInstanceOfSatisfying(
                    GeneralException.class,
                    g ->
                        assertThat(g.getErrorCode())
                            .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN)));

    // 성공한 요청이 받은 새 refresh token까지 폐기돼 살아 있는 토큰이 없다
    assertThat(aliveRefreshTokens(userId)).as("살아 있는 refresh token 수").isZero();
    assertThat(redisTemplate.hasKey("auth:revoked-before:" + userId))
        .as("access token 폐기 기준 시각")
        .isTrue();

    // 성공한 요청이 받은 access token도 경주 중에 기록된 기준 시각에 걸린다.
    // 아래 refresh가 재사용 감지로 기준 시각을 다시 쓰기 전에 확인해야 의미가 있다.
    AuthResDTO.Tokens winner = outcomes.successes().getFirst();
    me(winner.accessToken())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON401_1"));
    refresh(winner.refreshToken())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH401_3"));
  }

  private Outcomes refreshConcurrently(String refreshToken) throws InterruptedException {
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<AuthResDTO.Tokens>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            pool.submit(() -> {
              ready.countDown();
              start.await();
              return authService.refresh(new AuthReqDTO.Refresh(refreshToken));
            }));
      }
      ready.await();
      start.countDown();

      List<AuthResDTO.Tokens> successes = new ArrayList<>();
      List<Throwable> failures = new ArrayList<>();
      for (Future<AuthResDTO.Tokens> future : futures) {
        try {
          successes.add(future.get(30, TimeUnit.SECONDS));
        } catch (ExecutionException e) {
          failures.add(e.getCause());
        } catch (TimeoutException e) {
          throw new AssertionError("재발급이 30초 안에 끝나지 않았다", e);
        }
      }
      return new Outcomes(successes, failures);
    }
  }

  private long aliveRefreshTokens(long userId) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from refresh_tokens where user_id = ? and revoked_at is null",
            Long.class,
            userId);
    return count == null ? 0 : count;
  }

  private LoggedIn loginAs(String sub) throws Exception {
    given(googleAuthClient.verifyIdToken(sub))
        .willReturn(
            new GoogleAuthClientResDTO.IdToken(sub, sub + "@example.com", true, "여행자", null));
    String login =
        mockMvc
            .perform(
                post("/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idToken\":\"" + sub + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new LoggedIn(
        ((Number) JsonPath.read(login, "$.result.user.userId")).longValue(),
        JsonPath.read(login, "$.result.tokens.refreshToken"));
  }

  private ResultActions me(String accessToken) throws Exception {
    return mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mockMvc.perform(
        post("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
  }

  private record LoggedIn(long userId, String refreshToken) {}

  private record Outcomes(List<AuthResDTO.Tokens> successes, List<Throwable> failures) {}
}

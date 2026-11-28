package com.trupass.core.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.trupass.core.global.config.TrupassProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AccessTokenRevocationStoreTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  private final AccessTokenRevocationStore store;

  AccessTokenRevocationStoreTest() {
    given(redis.opsForValue()).willReturn(values);
    TrupassProperties properties =
        new TrupassProperties(
            null,
            null,
            null,
            null,
            null,
            new TrupassProperties.Auth(
                null,
                new TrupassProperties.Jwt(
                    null, null, "trupass", Duration.ofMinutes(30), Duration.ofDays(14))),
            null);
    store = new AccessTokenRevocationStore(redis, properties);
  }

  @Test
  void 기준_시각을_epoch_초로_access_token_수명만큼_저장한다() {
    store.revokeBefore(2L, Instant.ofEpochMilli(1_000_900));

    verify(values).set("auth:revoked-before:2", "1000", Duration.ofMinutes(30));
  }

  @Test
  void 저장된_기준_시각을_읽는다() {
    given(values.get("auth:revoked-before:2")).willReturn("1000");

    assertThat(store.revokedBefore(2L)).contains(Instant.ofEpochSecond(1000));
    assertThat(store.revokedBefore(3L)).isEmpty();
  }

  @Test
  void Redis를_읽지_못하면_기준_시각이_없는_것으로_보고_통과시킨다() {
    willThrow(new RedisConnectionFailureException("connection refused"))
        .given(values)
        .get(anyString());
    assertThat(store.revokedBefore(2L)).isEmpty();

    willThrow(new QueryTimeoutException("timeout")).given(values).get(anyString());
    assertThat(store.revokedBefore(2L)).isEmpty();
  }

  @Test
  void Redis에_쓰지_못해도_예외를_던지지_않는다() {
    willThrow(new RedisConnectionFailureException("connection refused"))
        .given(values)
        .set(anyString(), anyString(), any(Duration.class));

    assertThatCode(() -> store.revokeBefore(2L, Instant.now())).doesNotThrowAnyException();
  }
}

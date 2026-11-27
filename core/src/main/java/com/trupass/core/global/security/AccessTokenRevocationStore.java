package com.trupass.core.global.security;

import com.trupass.core.global.config.TrupassProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 사용자별 access token 폐기 기준 시각을 Redis에 둔다. 이 시각 이하에 발급된({@code iat <= 기준 시각}) access token은 {@link
 * RevokedBeforeValidator}가 거부한다.
 *
 * <p>키는 {@code auth:revoked-before:{userId}}, 값은 epoch 초다. JWT {@code iat}가 초 단위라 같은 단위로 저장한다. TTL은
 * access token 수명과 같다. 그 뒤에는 막을 토큰이 모두 만료돼 있다.
 *
 * <p>Redis 장애 때는 읽기·쓰기 모두 예외를 삼키고 WARN만 남긴다(fail-open). 이 기능은 인증의 필수 경로가 아니라 보안을 한 겹 더하는 장치라서, 꺼지면
 * 도입 전과 같은 상태(access token이 만료까지 유효)로 돌아간다.
 */
@Component
public class AccessTokenRevocationStore {
  private static final Logger log = LoggerFactory.getLogger(AccessTokenRevocationStore.class);
  private static final String KEY_PREFIX = "auth:revoked-before:";

  private final StringRedisTemplate redis;
  private final Duration ttl;

  public AccessTokenRevocationStore(StringRedisTemplate redis, TrupassProperties properties) {
    this.redis = redis;
    this.ttl = properties.auth().jwt().accessTokenTtl();
  }

  /** {@code at} 이하에 발급된 이 사용자의 access token을 모두 거부하게 한다. */
  public void revokeBefore(Long userId, Instant at) {
    try {
      redis.opsForValue().set(key(userId), String.valueOf(at.getEpochSecond()), ttl);
    } catch (DataAccessException e) {
      log.warn("access token 폐기 기준 시각을 Redis에 쓰지 못했다. userId={}", userId, e);
    }
  }

  /** 기준 시각. 없거나 Redis를 읽지 못하면 비어 있다. */
  public Optional<Instant> revokedBefore(Long userId) {
    try {
      String value = redis.opsForValue().get(key(userId));
      return Optional.ofNullable(value).map(v -> Instant.ofEpochSecond(Long.parseLong(v)));
    } catch (DataAccessException e) {
      // 인증된 요청마다 읽으므로 장애 중에는 요청 수만큼 찍힌다. 스택 트레이스는 빼고 원인만 남긴다.
      log.warn(
          "access token 폐기 기준 시각을 Redis에서 읽지 못해 통과시킨다. userId={}, cause={}", userId, e.toString());
      return Optional.empty();
    }
  }

  static String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}

package com.trupass.core.domain.auth.service;

import com.trupass.core.domain.auth.entity.RefreshToken;
import com.trupass.core.domain.auth.event.AccessTokenRevokedEvent;
import com.trupass.core.domain.auth.exception.AuthErrorCode;
import com.trupass.core.domain.auth.repository.RefreshTokenRepository;
import com.trupass.core.domain.user.event.UserWithdrawnEvent;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import com.trupass.core.global.config.TrupassProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh token 발급·교체·폐기. 원문은 사용자에게만 주고 DB에는 해시만 저장한다.
 *
 * <p>로그아웃 · 재사용 감지 · 탈퇴로 refresh token을 폐기할 때는 {@link AccessTokenRevokedEvent}도 발행해, 이미 발급한 access
 * token까지 막는다.
 */
@Service
public class RefreshTokenService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final TrupassProperties properties;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      TrupassProperties properties,
      Clock clock,
      ApplicationEventPublisher events) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.properties = properties;
    this.clock = clock;
    this.events = events;
  }

  /**
   * 회원이 탈퇴하면 그 회원의 refresh token을 모두 폐기하고 access token도 막는다. 탈퇴와 같은 트랜잭션에서 동기로 돌아, 탈퇴가 되돌려지면 폐기도 함께
   * 되돌려진다. access token 폐기 기록은 탈퇴가 커밋된 뒤에 남는다.
   */
  @EventListener
  @Transactional
  public void onUserWithdrawn(UserWithdrawnEvent event) {
    Instant now = clock.instant();
    refreshTokenRepository.revokeAllByUserId(event.userId(), now);
    events.publishEvent(new AccessTokenRevokedEvent(event.userId(), now));
  }

  /** 새 refresh token을 만들어 저장하고 원문을 돌려준다. */
  @Transactional
  public String issue(Long userId) {
    Instant now = clock.instant();
    String raw = newRawToken();
    refreshTokenRepository.save(
        new RefreshToken(
            userId, hash(raw), now.plus(properties.auth().jwt().refreshTokenTtl()), now));
    return raw;
  }

  /**
   * refresh token을 한 번 쓰고 폐기한다. 돌려준 userId로 새 토큰 쌍을 발급한다.
   *
   * <p>이미 폐기된 토큰이 다시 오면 탈취로 보고 그 사용자의 refresh token을 모두 폐기하고 access token도 막는다. 예외를 던지지만 {@code
   * noRollbackFor}라서 폐기는 커밋되고, access token 폐기 기록은 그 커밋 뒤에 남는다.
   */
  @Transactional(noRollbackFor = GeneralException.class)
  public Long consume(String rawToken) {
    Instant now = clock.instant();
    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN));
    if (token.isRevoked()) {
      refreshTokenRepository.revokeAllByUserId(token.getUserId(), now);
      events.publishEvent(new AccessTokenRevokedEvent(token.getUserId(), now));
      throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (token.isExpired(now)) {
      throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    token.revoke(now);
    return token.getUserId();
  }

  /**
   * 로그아웃. 이 refresh token을 폐기하고, 이 사용자의 access token을 모두 막는다(다른 기기도 401을 받고 자기 refresh token으로
   * 재발급한다). 없는 토큰이어도 성공으로 본다.
   */
  @Transactional
  public void revoke(String rawToken) {
    Instant now = clock.instant();
    refreshTokenRepository
        .findByTokenHash(hash(rawToken))
        .ifPresent(token -> {
          token.revoke(now);
          events.publishEvent(new AccessTokenRevokedEvent(token.getUserId(), now));
        });
  }

  private static String newRawToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

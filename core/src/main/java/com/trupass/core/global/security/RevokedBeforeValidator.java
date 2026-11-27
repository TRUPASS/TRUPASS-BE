package com.trupass.core.global.security;

import java.time.Instant;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 로그아웃 · refresh token 재사용 감지 · 회원 탈퇴로 폐기된 access token을 거부한다.
 *
 * <p>{@code iat <= 기준 시각}이면 거부한다. {@code iat}는 초 단위라서 기준 시각과 같은 초에 발급된 토큰은 폐기 전인지 후인지 알 수 없다.
 * {@code <}로 두면 폐기 직전에 발급된 토큰(탈취자 것일 수 있다)이 30분간 살아남고, {@code <=}로 두면 직후에 발급된 정상 토큰이 401을 한 번 받는다.
 * 후자는 FE가 재발급으로 스스로 복구하므로 {@code <=}를 쓴다.
 *
 * <p>거부되면 {@link ApiAuthenticationHandlers}가 {@code COMMON401_1}로 응답한다.
 */
public class RevokedBeforeValidator implements OAuth2TokenValidator<Jwt> {
  private static final OAuth2TokenValidatorResult REVOKED =
      OAuth2TokenValidatorResult.failure(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "폐기된 access token입니다.", null));
  private static final OAuth2TokenValidatorResult MALFORMED =
      OAuth2TokenValidatorResult.failure(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "sub 또는 iat가 없는 access token입니다.", null));

  private final AccessTokenRevocationStore store;

  public RevokedBeforeValidator(AccessTokenRevocationStore store) {
    this.store = store;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    Instant issuedAt = jwt.getIssuedAt();
    Long userId = userId(jwt.getSubject());
    if (issuedAt == null || userId == null) {
      return MALFORMED; // Trupass가 발급한 토큰에는 항상 있다. 없으면 기준 시각과 비교할 수 없으니 거부한다.
    }
    Optional<Instant> cutoff = store.revokedBefore(userId);
    if (cutoff.isPresent() && issuedAt.getEpochSecond() <= cutoff.get().getEpochSecond()) {
      return REVOKED;
    }
    return OAuth2TokenValidatorResult.success();
  }

  private static Long userId(String subject) {
    try {
      return subject == null ? null : Long.valueOf(subject);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

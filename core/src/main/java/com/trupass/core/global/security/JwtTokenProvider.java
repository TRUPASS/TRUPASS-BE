package com.trupass.core.global.security;

import com.trupass.core.global.config.TrupassProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Trupass access token(JWT, HS256) 발급·검증. {@code sub}가 사용자 ID다.
 *
 * <p>검증은 서명·만료·발급자에 더해, 로그아웃 등으로 폐기된 토큰인지를 Redis 기준 시각으로 확인한다({@link RevokedBeforeValidator}).
 *
 * <p>서명 키는 {@code trupass.auth.jwt.secret}(환경 변수 {@code TRUPASS_JWT_SECRET})이고 32바이트 이상이어야 한다.
 */
@Component
public class JwtTokenProvider {
  private static final int MIN_SECRET_BYTES = 32;

  private final JwtEncoder encoder;
  private final NimbusJwtDecoder decoder;
  private final TrupassProperties.Jwt jwtProperties;
  private final Clock clock;

  public JwtTokenProvider(
      TrupassProperties properties, Clock clock, AccessTokenRevocationStore revocationStore) {
    this.jwtProperties = properties.auth().jwt();
    this.clock = clock;
    SecretKey key = secretKey(effectiveSecret(jwtProperties));
    this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
    timestampValidator.setClock(clock);
    // 만료 → 발급자 → 폐기 순. 기본값은 앞이 실패해도 끝까지 돌기 때문에 failOnError로 멈추게 해서,
    // 만료됐거나 다른 발급자의 토큰이면 Redis까지 가지 않게 한다.
    DelegatingOAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            timestampValidator,
            new JwtIssuerValidator(jwtProperties.issuer()),
            new RevokedBeforeValidator(revocationStore));
    validator.setFailOnError(true);
    this.decoder.setJwtValidator(validator);
  }

  /** {@code TRUPASS_JWT_SECRET}이 비어 있으면 local 프로필의 개발용 키를 쓴다. prod에는 개발용 키가 없다. */
  static String effectiveSecret(TrupassProperties.Jwt jwt) {
    if (jwt.secret() != null && !jwt.secret().isBlank()) {
      return jwt.secret();
    }
    return jwt.localDevSecret();
  }

  private static SecretKey secretKey(String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "trupass.auth.jwt.secret(TRUPASS_JWT_SECRET)은 32바이트 이상이어야 합니다. 예: openssl rand -base64 48");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  public String createAccessToken(Long userId) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** Spring Security resource server가 {@code Authorization: Bearer} 토큰을 검증할 때 쓴다. */
  public JwtDecoder decoder() {
    return decoder;
  }
}

package com.trupass.core.domain.auth.client.google;

import com.trupass.core.domain.auth.client.GoogleAuthClient;
import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;
import com.trupass.core.domain.auth.exception.AuthErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Google 공개키(JWKS)로 ID 토큰을 직접 검증한다. 공개키는 Nimbus가 캐시하므로 로그인마다 Google을 부르지는 않는다.
 *
 * @see <a
 *     href="https://developers.google.com/identity/gsi/web/guides/verify-google-id-token">Verify
 *     the Google ID token</a>
 */
public class GoogleIdTokenClient implements GoogleAuthClient {
  static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
  static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

  private final List<String> clientIds;
  private final JwtDecoder decoder;

  public GoogleIdTokenClient(
      List<String> clientIds, Duration connectTimeout, Duration readTimeout) {
    this(clientIds, jwkSetDecoder(connectTimeout, readTimeout));
  }

  /** 테스트에서 공개키를 바꿔 끼울 수 있게 decoder를 받는다. 검증 규칙은 여기서 붙인다. */
  GoogleIdTokenClient(List<String> clientIds, NimbusJwtDecoder decoder) {
    this.clientIds = clientIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            new JwtClaimValidator<Object>(
                JwtClaimNames.ISS, iss -> iss != null && ISSUERS.contains(iss.toString())),
            new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.stream().anyMatch(this.clientIds::contains))));
    this.decoder = decoder;
  }

  private static NimbusJwtDecoder jwkSetDecoder(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .restOperations(new RestTemplate(requestFactory))
        .build();
  }

  @Override
  public GoogleAuthClientResDTO.IdToken verifyIdToken(String idToken) {
    if (clientIds.isEmpty()) {
      throw new GeneralException(AuthErrorCode.GOOGLE_LOGIN_NOT_CONFIGURED);
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(idToken);
    } catch (BadJwtException e) {
      // 서명·형식·만료·aud·iss 검증 실패
      throw new GeneralException(AuthErrorCode.INVALID_GOOGLE_TOKEN, e);
    } catch (JwtException e) {
      // 공개키(JWKS)를 받아오지 못한 경우 등
      throw new GeneralException(AuthErrorCode.GOOGLE_UNAVAILABLE, e);
    }
    String email = jwt.getClaimAsString("email");
    if (jwt.getSubject() == null || email == null) {
      throw new GeneralException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
    }
    return new GoogleAuthClientResDTO.IdToken(
        jwt.getSubject(),
        email,
        Boolean.parseBoolean(String.valueOf(jwt.getClaims().get("email_verified"))),
        jwt.getClaimAsString("name"),
        jwt.getClaimAsString("picture"));
  }
}

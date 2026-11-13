package com.trupass.core.domain.auth.client.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;
import com.trupass.core.domain.auth.exception.AuthErrorCode;
import com.trupass.core.global.apiPayload.exception.GeneralException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Google 대신 테스트용 RSA 키로 서명한 ID 토큰으로 검증 규칙을 확인한다. */
class GoogleIdTokenClientTest {
  private static final String CLIENT_ID = "trupass-web.apps.googleusercontent.com";

  private final KeyPair keyPair = rsaKeyPair();
  private final NimbusJwtEncoder encoder =
      new NimbusJwtEncoder(
          new ImmutableJWKSet<>(
              new JWKSet(
                  new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                      .privateKey((RSAPrivateKey) keyPair.getPrivate())
                      .keyID("test")
                      .build())));

  private GoogleIdTokenClient client(List<String> clientIds) {
    return new GoogleIdTokenClient(
        clientIds, NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build());
  }

  private String idToken(Consumer<JwtClaimsSet.Builder> customizer) {
    Instant now = Instant.now();
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer("https://accounts.google.com")
            .audience(List.of(CLIENT_ID))
            .subject("1234567890")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("email", "traveler@example.com")
            .claim("email_verified", true)
            .claim("name", "여행자")
            .claim("picture", "https://example.com/p.png");
    customizer.accept(claims);
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  @Test
  void 올바른_ID_토큰이면_계정_정보를_돌려준다() {
    GoogleAuthClientResDTO.IdToken result =
        client(List.of("other-client", CLIENT_ID)).verifyIdToken(idToken(c -> {}));

    assertThat(result)
        .isEqualTo(
            new GoogleAuthClientResDTO.IdToken(
                "1234567890", "traveler@example.com", true, "여행자", "https://example.com/p.png"));
  }

  @Test
  void 스킴_없는_issuer도_받는다() {
    var result =
        client(List.of(CLIENT_ID)).verifyIdToken(idToken(c -> c.issuer("accounts.google.com")));
    assertThat(result.sub()).isEqualTo("1234567890");
  }

  @Test
  void 다른_앱용으로_발급된_토큰은_거부한다() {
    assertRejected(idToken(c -> c.audience(List.of("someone-else"))));
  }

  @Test
  void Google이_발급하지_않은_토큰은_거부한다() {
    assertRejected(idToken(c -> c.issuer("https://evil.example.com")));
  }

  @Test
  void 만료된_토큰은_거부한다() {
    Instant past = Instant.now().minusSeconds(7200);
    assertRejected(idToken(c -> c.issuedAt(past).expiresAt(past.plusSeconds(60))));
  }

  @Test
  void 형식이_틀린_토큰은_거부한다() {
    assertRejected("not-a-jwt");
  }

  @Test
  void client_ID가_설정되지_않으면_설정_오류로_알린다() {
    assertThatThrownBy(() -> client(List.of(" ")).verifyIdToken(idToken(c -> {})))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(AuthErrorCode.GOOGLE_LOGIN_NOT_CONFIGURED);
  }

  private void assertRejected(String token) {
    assertThatThrownBy(() -> client(List.of(CLIENT_ID)).verifyIdToken(token))
        .isInstanceOf(GeneralException.class)
        .extracting(e -> ((GeneralException) e).getErrorCode())
        .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
  }

  private static KeyPair rsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}

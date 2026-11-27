package com.trupass.core.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RevokedBeforeValidatorTest {
  private static final Instant CUTOFF = Instant.ofEpochSecond(1000);

  private final AccessTokenRevocationStore store = mock(AccessTokenRevocationStore.class);
  private final RevokedBeforeValidator validator = new RevokedBeforeValidator(store);

  private static Jwt token(String subject, Instant issuedAt) {
    Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256").claim("iss", "trupass");
    if (subject != null) {
      builder.subject(subject);
    }
    if (issuedAt != null) {
      builder.issuedAt(issuedAt);
    }
    return builder.build();
  }

  private boolean rejects(Instant issuedAt) {
    return validator.validate(token("2", issuedAt)).hasErrors();
  }

  @Test
  void 기준_시각_이하에_발급된_토큰은_거부하고_이후는_통과한다() {
    given(store.revokedBefore(2L)).willReturn(Optional.of(CUTOFF));

    assertThat(rejects(Instant.ofEpochSecond(999))).isTrue();
    assertThat(rejects(Instant.ofEpochSecond(1000))).isTrue(); // 같은 초: 전후를 알 수 없어 거부한다
    assertThat(rejects(Instant.ofEpochSecond(1001))).isFalse();
  }

  @Test
  void 같은_초면_밀리초가_뒤여도_거부한다() {
    given(store.revokedBefore(2L)).willReturn(Optional.of(Instant.ofEpochMilli(1_000_300)));

    assertThat(rejects(Instant.ofEpochMilli(1_000_900))).isTrue();
  }

  @Test
  void 기준_시각이_없으면_통과한다() {
    // 폐기된 적이 없거나, Redis를 읽지 못한 경우(fail-open)
    given(store.revokedBefore(2L)).willReturn(Optional.empty());

    assertThat(rejects(Instant.ofEpochSecond(1))).isFalse();
  }

  @Test
  void sub나_iat가_없거나_sub가_숫자가_아니면_거부한다() {
    assertThat(validator.validate(token(null, CUTOFF)).hasErrors()).isTrue();
    assertThat(validator.validate(token("2", null)).hasErrors()).isTrue();
    assertThat(validator.validate(token("abc", CUTOFF)).hasErrors()).isTrue();
  }
}

package com.trupass.core.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trupass.core.global.config.TrupassProperties;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtTokenProviderTest {
  private static final String SECRET = "a".repeat(32);
  private static final String DEV_SECRET = "d".repeat(32);

  private static TrupassProperties.Jwt jwt(String secret, String localDevSecret) {
    return new TrupassProperties.Jwt(
        secret, localDevSecret, "trupass", Duration.ofMinutes(30), Duration.ofDays(14));
  }

  @Test
  void secret이_있으면_그_값을_쓴다() {
    assertThat(JwtTokenProvider.effectiveSecret(jwt(SECRET, DEV_SECRET))).isEqualTo(SECRET);
  }

  @Test
  void secret이_없거나_빈_값이면_개발용_키를_쓴다() {
    assertThat(JwtTokenProvider.effectiveSecret(jwt(null, DEV_SECRET))).isEqualTo(DEV_SECRET);
    assertThat(JwtTokenProvider.effectiveSecret(jwt("", DEV_SECRET))).isEqualTo(DEV_SECRET);
    assertThat(JwtTokenProvider.effectiveSecret(jwt("  ", DEV_SECRET))).isEqualTo(DEV_SECRET);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(TrupassProperties.class)
  static class PropertiesConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfig.class)
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(JwtTokenProvider.class);

  @Test
  void 환경_변수가_빈_값이어도_개발용_키가_있으면_뜬다() {
    runner
        .withPropertyValues(
            "trupass.auth.jwt.secret=", "trupass.auth.jwt.local-dev-secret=" + DEV_SECRET)
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void 운영처럼_개발용_키가_없으면_secret_없이는_뜨지_않는다() {
    runner
        .withPropertyValues("trupass.auth.jwt.secret=")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void secret이_32바이트보다_짧으면_거부한다() {
    assertThatThrownBy(() ->
            new JwtTokenProvider(
                new TrupassProperties(
                    null,
                    null,
                    null,
                    null,
                    null,
                    new TrupassProperties.Auth(null, jwt("short", null))),
                Clock.systemUTC()))
        .isInstanceOf(IllegalStateException.class);
  }
}

package com.trupass.core.global.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 세션 없이 {@code Authorization: Bearer {accessToken}}으로 인증한다.
 *
 * <p>열린 경로({@link #PUBLIC_PATHS}) 말고는 모두 로그인이 필요하다. {@code /internal/**}은 agent 전용이라 토큰 없이 열어 두고,
 * 운영에서는 Caddy가 외부 접근을 막는다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig implements WebMvcConfigurer {

  static final String[] PUBLIC_PATHS = {
    "/auth/**",
    "/internal/**",
    "/webhooks/**",
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/info",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/error",
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtTokenProvider jwtTokenProvider, ApiAuthenticationHandlers handlers)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 ->
            oauth2
                .jwt(jwt -> jwt.decoder(jwtTokenProvider.decoder()))
                .authenticationEntryPoint(handlers)
                .accessDeniedHandler(handlers))
        .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
        .build();
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new LoginUserIdArgumentResolver());
  }
}

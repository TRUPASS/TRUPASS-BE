package com.trupass.core.global.config;

import com.trupass.core.global.common.IdempotencyKey;
import com.trupass.core.global.web.CheckoutMdc;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * FE(로컬 {@code pnpm dev} 5173, 배포 시 Vercel 도메인)에서 core를 직접 호출할 수 있게 연다. Spring Security의 CORS 필터가 이
 * 설정을 쓰므로, 인증이 필요한 경로의 preflight(OPTIONS)도 토큰 없이 통과한다.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

  @Bean
  CorsConfigurationSource corsConfigurationSource(TrupassProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.cors().allowedOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("*"));
    cors.setExposedHeaders(List.of(IdempotencyKey.HEADER, CheckoutMdc.HEADER));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }
}

package com.trupass.core.global.config;

import com.trupass.core.global.common.IdempotencyKey;
import com.trupass.core.global.web.CheckoutMdc;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** FE(로컬 {@code pnpm dev} 5173, 배포 시 Vercel 도메인)에서 core를 직접 호출할 수 있게 연다. */
@Configuration(proxyBeanMethods = false)
public class CorsConfig implements WebMvcConfigurer {
  private final TrupassProperties properties;

  public CorsConfig(TrupassProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders(IdempotencyKey.HEADER, CheckoutMdc.HEADER);
  }
}

package com.trupass.core.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code /v3/api-docs}, {@code /swagger-ui}. FE는 이 스펙으로 타입을 생성한다. */
@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {

  @Bean
  OpenAPI trupassOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Trupass Core API")
                .version("v0")
                .description("Trupass 메인 API · 주문 진행 · 결제 · 원장"));
  }
}

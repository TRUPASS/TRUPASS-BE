package com.trupass.core.global.config;

import com.trupass.core.global.security.LoginUserId;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code /v3/api-docs}, {@code /swagger-ui}. FE는 이 스펙으로 타입을 생성한다. Swagger UI의 Authorize 버튼에 access
 * token을 넣으면 인증이 필요한 API도 호출할 수 있다.
 */
@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {
  private static final String BEARER = "bearerAuth";

  static {
    // @LoginUserId는 access token에서 채운다. 스펙에 요청 파라미터(userId)로 나오면 FE 생성 타입이 userId를 요구하게 된다.
    SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUserId.class);
  }

  @Bean
  OpenAPI trupassOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Trupass Core API")
                .version("v0")
                .description("Trupass 메인 API · 주문 진행 · 결제 · 원장"))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}

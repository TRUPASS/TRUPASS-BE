package com.trupass.core.config

import com.trupass.core.config.http.CheckoutMdc
import com.trupass.core.config.http.IDEMPOTENCY_KEY_HEADER
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class WebConfig(
    private val properties: TrupassProperties,
) : WebMvcConfigurer {
    /** FE(로컬 `pnpm dev` 5173, 배포 시 Vercel 도메인)에서 core를 직접 호출할 수 있게 연다. */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOrigins(*properties.cors.allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders(IDEMPOTENCY_KEY_HEADER, CheckoutMdc.HEADER)
    }

    /** `/v3/api-docs`, `/swagger-ui`. FE는 이 스펙으로 타입을 생성한다. */
    @Bean
    fun trupassOpenApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Trupass Core API")
                .version("v0")
                .description("Trupass 메인 API · Saga · 원장 · 결제"),
        )
}

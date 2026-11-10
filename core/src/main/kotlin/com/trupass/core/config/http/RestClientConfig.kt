package com.trupass.core.config.http

import com.trupass.core.config.TrupassProperties
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import java.net.http.HttpClient

/**
 * 모든 외부 HTTP 호출이 공유하는 RestClient 설정.
 * 어댑터는 `RestClient.Builder`를 주입받아 `clone().baseUrl(...)`만 붙여 쓴다.
 *
 * - 연결·읽기 타임아웃 기본값(`trupass.http.*`)
 * - [CheckoutContextInterceptor]: MDC의 checkout_id를 헤더로 전파하고 호출 결과를 로그로 남긴다.
 * - `Idempotency-Key` 헤더는 [idempotencyKey] 확장 함수로 붙인다.
 */
@Configuration(proxyBeanMethods = false)
class RestClientConfig {
    @Bean
    fun trupassRestClientCustomizer(properties: TrupassProperties): RestClientCustomizer =
        RestClientCustomizer { builder ->
            val httpClient =
                HttpClient
                    .newBuilder()
                    .connectTimeout(properties.http.connectTimeout)
                    .build()
            val requestFactory =
                JdkClientHttpRequestFactory(httpClient).apply {
                    setReadTimeout(properties.http.readTimeout)
                }
            builder
                .requestFactory(requestFactory)
                .requestInterceptor(CheckoutContextInterceptor())
        }
}

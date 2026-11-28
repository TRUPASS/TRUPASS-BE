package com.trupass.core.global.config;

import com.trupass.core.global.web.CheckoutContextInterceptor;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * 모든 외부 HTTP 호출이 공유하는 RestClient 설정. client 구현체는 {@code RestClient.Builder}를 받아 {@code
 * clone().baseUrl(...)}만 붙여 쓴다.
 *
 * <ul>
 *   <li>연결·읽기 타임아웃 기본값({@code trupass.http.*})
 *   <li>{@link CheckoutContextInterceptor}: MDC의 checkout_id를 헤더로 전파하고 호출 결과를 로그로 남긴다.
 * </ul>
 *
 * <p>검색·AI처럼 5초보다 오래 걸리는 호출은 {@link #requestFactory}로 read-timeout만 바꿔 끼운다. 인터셉터는 그대로 남는다.
 */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

  @Bean
  RestClientCustomizer trupassRestClientCustomizer(TrupassProperties properties) {
    return builder ->
        builder
            .requestFactory(
                requestFactory(properties.http().connectTimeout(), properties.http().readTimeout()))
            .requestInterceptor(new CheckoutContextInterceptor());
  }

  /**
   * HTTP/1.1로 고정한다. JDK 기본값(HTTP/2)은 평문 http에서 {@code Upgrade: h2c}를 붙이는데, agent(uvicorn)가 이 업그레이드를
   * 거절하면서 POST 본문을 버린다. 외부 API는 호출 수가 적어 HTTP/2로 얻는 이득이 거의 없다.
   */
  static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return requestFactory;
  }
}

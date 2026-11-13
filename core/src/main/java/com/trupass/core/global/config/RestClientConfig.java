package com.trupass.core.global.config;

import com.trupass.core.global.web.CheckoutContextInterceptor;
import java.net.http.HttpClient;
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
 */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

  @Bean
  RestClientCustomizer trupassRestClientCustomizer(TrupassProperties properties) {
    return builder -> {
      HttpClient httpClient =
          HttpClient.newBuilder().connectTimeout(properties.http().connectTimeout()).build();
      JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
      requestFactory.setReadTimeout(properties.http().readTimeout());
      builder.requestFactory(requestFactory).requestInterceptor(new CheckoutContextInterceptor());
    };
  }
}

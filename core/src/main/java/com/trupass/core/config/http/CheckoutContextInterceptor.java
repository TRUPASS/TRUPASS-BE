package com.trupass.core.config.http;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 외부 호출마다 현재 MDC의 {@code checkout_id}를 {@code X-Checkout-Id} 헤더로 전파하고, 메서드·URI·상태·소요 시간·멱등키를 구조화
 * 로그로 남긴다.
 *
 * <p>MDC에 checkout_id를 넣는 쪽: 들어오는 요청은 {@code CheckoutIdMdcFilter}, Saga 실행은 오케스트레이터가 {@code
 * MDC.putCloseable(CheckoutMdc.KEY, id)}로 감싼다.
 */
public class CheckoutContextInterceptor implements ClientHttpRequestInterceptor {
  private static final Logger log = LoggerFactory.getLogger(CheckoutContextInterceptor.class);

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String checkoutId = MDC.get(CheckoutMdc.KEY);
    if (checkoutId != null && !request.getHeaders().containsHeader(CheckoutMdc.HEADER)) {
      request.getHeaders().set(CheckoutMdc.HEADER, checkoutId);
    }
    String idempotencyKey = request.getHeaders().getFirst(IdempotencyKeys.HEADER);
    long startedAt = System.nanoTime();
    try {
      ClientHttpResponse response = execution.execute(request, body);
      log.atInfo()
          .addKeyValue("http.method", request.getMethod().name())
          .addKeyValue("url", request.getURI().toString())
          .addKeyValue("http.status", response.getStatusCode().value())
          .addKeyValue("duration_ms", elapsedMs(startedAt))
          .addKeyValue("idempotency_key", idempotencyKey)
          .log("outbound http call");
      return response;
    } catch (IOException | RuntimeException e) {
      log.atWarn()
          .addKeyValue("http.method", request.getMethod().name())
          .addKeyValue("url", request.getURI().toString())
          .addKeyValue("duration_ms", elapsedMs(startedAt))
          .addKeyValue("idempotency_key", idempotencyKey)
          .setCause(e)
          .log("outbound http call failed");
      throw e;
    }
  }

  private static long elapsedMs(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }
}

package com.trupass.core.global.web;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.trupass.core.global.common.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CheckoutContextInterceptorTest {

  @Test
  void MDC의_checkout_id와_멱등키가_헤더로_나간다() {
    RestClient.Builder builder =
        RestClient.builder().requestInterceptor(new CheckoutContextInterceptor());
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient client = builder.baseUrl("http://merchant.test").build();
    IdempotencyKey key = IdempotencyKey.of("co_1", "A", "HOLD");

    server
        .expect(requestTo("http://merchant.test/merchants/A/holds"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(CheckoutMdc.HEADER, "co_1"))
        .andExpect(header(IdempotencyKey.HEADER, "co_1:A:HOLD"))
        .andRespond(withSuccess());

    try (MDC.MDCCloseable ignored = MDC.putCloseable(CheckoutMdc.KEY, "co_1")) {
      client
          .post()
          .uri("/merchants/{id}/holds", "A")
          .headers(key.asHeader())
          .retrieve()
          .toBodilessEntity();
    }
    server.verify();
  }
}

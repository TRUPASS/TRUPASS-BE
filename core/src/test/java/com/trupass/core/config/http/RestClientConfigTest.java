package com.trupass.core.config.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.trupass.core.domain.checkout.CheckoutId;
import com.trupass.core.domain.checkout.IdempotencyKey;
import com.trupass.core.domain.checkout.MerchantId;
import com.trupass.core.domain.saga.SagaStepType;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

  private final IdempotencyKey key =
      IdempotencyKey.of(new CheckoutId("co_1"), new MerchantId("A"), SagaStepType.HOLD);

  @Test
  void 멱등키는_checkout_id_merchant_step_형식이다() {
    assertThat(key.value()).isEqualTo("co_1:A:HOLD");
  }

  @Test
  void MDC의_checkout_id와_멱등키가_헤더로_나간다() {
    RestClient.Builder builder =
        RestClient.builder().requestInterceptor(new CheckoutContextInterceptor());
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient client = builder.baseUrl("http://merchant.test").build();

    server
        .expect(requestTo("http://merchant.test/merchants/A/holds"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(CheckoutMdc.HEADER, "co_1"))
        .andExpect(header(IdempotencyKeys.HEADER, "co_1:A:HOLD"))
        .andRespond(withSuccess());

    try (MDC.MDCCloseable ignored = MDC.putCloseable(CheckoutMdc.KEY, "co_1")) {
      client
          .post()
          .uri("/merchants/{id}/holds", "A")
          .headers(IdempotencyKeys.header(key))
          .retrieve()
          .toBodilessEntity();
    }
    server.verify();
  }
}
